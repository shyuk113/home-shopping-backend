package com.shop.backend.global.seed;

import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemBulkRepository;
import com.shop.backend.Item.infrastructure.ItemBulkRepository.IdPrice;
import com.shop.backend.Item.infrastructure.ItemBulkRepository.ItemRow;
import com.shop.backend.order.domain.OrderStatus;
import com.shop.backend.order.infrastructure.OrderBulkRepository;
import com.shop.backend.order.infrastructure.OrderBulkRepository.OrderItemRow;
import com.shop.backend.order.infrastructure.OrderBulkRepository.OrderRow;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로컬 대용량 더미 데이터 시더. --seed.enabled=true 로 실행할 때만 동작한다.
 * 이미 목표 건수만큼 있으면 건너뛰고, 중간에 끊겼으면 남은 건수만 이어서 적재한다.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DummyDataSeeder implements ApplicationRunner {

    private static final int ITEM_CHUNK = 10_000;   // 트랜잭션 1개당 상품 수
    private static final int ORDER_CHUNK = 10_000;  // 트랜잭션 1개당 주문 수
    private static final String[] CITIES = {"서울", "부산", "인천", "대구", "대전", "광주", "수원", "울산"};
    private static final ItemCategory[] CATEGORIES = ItemCategory.values();

    private final ItemBulkRepository itemBulkRepository;
    private final OrderBulkRepository orderBulkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${seed.item-count:100000}")
    private int itemCount;

    @Value("${seed.order-count:10000000}")
    private long orderCount;

    @Value("${seed.days:365}")
    private int days;   // 주문 시각을 최근 N일에 분산

    private static final int SELLER_COUNT = 1_000;

    @Override
    public void run(ApplicationArguments args) {
        seedSellers();
        seedItems();
        seedOrders();
    }

    private void seedSellers(){
        Long existing =jdbcTemplate.queryForObject("SELECT count(*) FROM seller", Long.class);
        if(existing>=SELLER_COUNT) return;
        jdbcTemplate.update("""
INSERT INTO seller (name, created_at, updated_at) SELECT '판매자-' || gs, now(), now() FROM generate_series(?, ?) gs """, existing+1, SELLER_COUNT);
    }


    private void seedItems() {
        long existing = itemBulkRepository.count();
        if (existing >= itemCount) {
            log.info("[seed] item {}건 존재, 건너뜀", existing);
            return;
        }
        long start = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(days);

        for (long offset = existing; offset < itemCount; offset += ITEM_CHUNK) {
            int size = (int) Math.min(ITEM_CHUNK, itemCount - offset);
            List<ItemRow> rows = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                long no = offset + i + 1;
                long sellerId = (no - 1) % SELLER_COUNT + 1;
                rows.add(new ItemRow(sellerId,
                        "상품-" + no,
                        random.nextInt(10, 500) * 100,          // 1,000 ~ 49,900원
                        random.nextInt(1, 1_000),
                        "더미 상품 " + no,
                        ItemStatus.SELLING,
                        CATEGORIES[random.nextInt(CATEGORIES.length)],
                        createdAt));
            }
            transactionTemplate.executeWithoutResult(status -> itemBulkRepository.bulkInsert(rows));
        }
        log.info("[seed] item {}건 적재 완료 ({} ms)", itemCount - existing, System.currentTimeMillis() - start);
    }

    private void seedOrders() {
        long existing = orderBulkRepository.count();
        if (existing >= orderCount) {
            log.info("[seed] orders {}건 존재, 건너뜀", existing);
            return;
        }
        List<Long> memberIds = jdbcTemplate.queryForList("SELECT id FROM member", Long.class);
        List<IdPrice> items = itemBulkRepository.findAllIdAndPrice();
        if (memberIds.isEmpty() || items.isEmpty()) {
            throw new IllegalStateException("member/item 데이터가 먼저 필요합니다.");
        }

        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        long rangeSeconds = Duration.ofDays(days).toSeconds();
        long inserted = 0;

        for (long done = existing; done < orderCount; done += ORDER_CHUNK) {
            int size = (int) Math.min(ORDER_CHUNK, orderCount - done);
            ThreadLocalRandom random = ThreadLocalRandom.current();

            List<Long> ids = orderBulkRepository.reserveIds(size);
            List<OrderRow> orders = new ArrayList<>(size);
            List<OrderItemRow> orderItems = new ArrayList<>(size * 2);

            for (Long orderId : ids) {
                LocalDateTime orderTime = now.minusSeconds(random.nextLong(rangeSeconds));
                orders.add(new OrderRow(
                        orderId,
                        memberIds.get(random.nextInt(memberIds.size())),
                        CITIES[random.nextInt(CITIES.length)],
                        "더미로 " + random.nextInt(1, 500),
                        String.format("%05d", random.nextInt(100_000)),
                        orderTime,
                        randomStatus(random)));

                int lineCount = random.nextInt(1, 4);   // 주문당 상품 1~3개
                for (int i = 0; i < lineCount; i++) {
                    IdPrice item = items.get(random.nextInt(items.size()));
                    orderItems.add(new OrderItemRow(orderId, item.id(), item.price(), random.nextInt(1, 4)));
                }
            }

            transactionTemplate.executeWithoutResult(status -> {
                orderBulkRepository.bulkInsertOrders(orders);
                orderBulkRepository.bulkInsertOrderItems(orderItems);
            });

            inserted += size;
            if (inserted % 500_000 == 0) {
                log.info("[seed] orders {}/{} ({} ms)", existing + inserted, orderCount,
                        System.currentTimeMillis() - start);
            }
        }
        log.info("[seed] orders {}건 적재 완료 ({} ms)", inserted, System.currentTimeMillis() - start);
    }

    /** PAID 85% / CANCEL 7% / FAILED 3% / PENDING 5% */
    private OrderStatus randomStatus(ThreadLocalRandom random) {
        int p = random.nextInt(100);
        if (p < 85) return OrderStatus.PAID;
        if (p < 92) return OrderStatus.CANCEL;
        if (p < 95) return OrderStatus.FAILED;
        return OrderStatus.PENDING;
    }
}
