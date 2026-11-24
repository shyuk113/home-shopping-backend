package com.shop.backend.Item.application.stock;

import com.shop.backend.Item.infrastructure.ItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.strategy", havingValue = "redis-gate")
public class RedisGateStockDecreaser implements StockDecreaser {

    private final ItemRepository itemRepository;
    private final StringRedisTemplate redis;

    public static final String KEY_PREFIX = "stock:gate:";

    @Override
    public boolean decrease(Long itemId, int quantity) {
        String key = KEY_PREFIX + itemId;
        ensureInitialized(key, itemId);

        Long remain = redis.opsForValue().decrement(key,quantity);
        if(remain == null || remain < 0){
            redis.opsForValue().increment(key, quantity);
            return false;
        }
        // 2) 이후 DB 트랜잭션이 롤백되면(재고 부족, 결제 오류 등) Redis 차감도 되돌림
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    redis.opsForValue().increment(key, quantity);
                }
            }
        });

        // 3) 최종 정합성은 DB가 보장
        if (itemRepository.decreaseStock(itemId, quantity) == 1) {
            return true;
        }
        log.warn("[재고 불일치] Redis 통과 후 DB 재고 부족 itemId={}", itemId);  // 롤백 훅이 Redis를 되돌림
        return false;
    }

    // 키가 없으면 DB 재고로 초기화. SETNX라 여러 요청이 동시에 초기화해도 한 번만 설정됨
    private void ensureInitialized(String key, Long itemId) {
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return;
        }
        int dbQuantity = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("존재 하지 않는 상품입니다."))
                .getQuantity();
        redis.opsForValue().setIfAbsent(key, String.valueOf(dbQuantity));
    }
}

