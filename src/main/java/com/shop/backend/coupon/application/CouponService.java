package com.shop.backend.coupon.application;

import com.shop.backend.coupon.domain.Coupon;
import com.shop.backend.coupon.domain.CouponIssueResult;
import com.shop.backend.coupon.infrastructure.CouponIssueProducer;
import com.shop.backend.coupon.infrastructure.CouponRepository;
import com.shop.backend.coupon.infrastructure.MemberCouponRepository;
import com.shop.backend.coupon.application.dto.request.CouponCreateRequestDto;
import com.shop.backend.coupon.application.dto.response.CouponResponse;
import com.shop.backend.global.exception.CouponOutOfStockException;
import com.shop.backend.global.exception.DuplicateCouponException;
import jakarta.persistence.EntityNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private static final String META_KEY = "coupon:{%d}:meta";
    private static final String MEMBERS_KEY = "coupon:{%d}:members";

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> couponIssueScript;
    private final CouponIssueProducer couponIssueProducer;

    private static long toMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Transactional
    public Long createCoupon(CouponCreateRequestDto request){
        Coupon coupon = Coupon.builder()
            .name(request.name())
            .discountAmount(request.discountAmount())
            .totalQuantity(request.totalQuantity())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .build();
        couponRepository.save(coupon);

        // DB 커밋이 성공한 뒤에만 Redis에 쿠폰 정보를 올림 (롤백되면 Redis에 유령 쿠폰이 남는 것 방지)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String metaKey = String.format(META_KEY, coupon.getId());
                redisTemplate.opsForHash().putAll(metaKey, Map.of(
                        "total", String.valueOf(coupon.getTotalQuantity()),
                        "start", String.valueOf(toMillis(coupon.getStartTime())),
                        "end", String.valueOf(toMillis(coupon.getEndTime()))));
                Instant expireAt = Instant.ofEpochMilli(toMillis(coupon.getEndTime())).plus(Duration.ofDays(7));
                redisTemplate.expireAt(metaKey, expireAt);
                redisTemplate.expireAt(String.format(MEMBERS_KEY,coupon.getId()), expireAt);
            }
        });
        return coupon.getId();
    }

    // DB에 접근하지 않음. Redis 판정 후 Kafka로 넘기고 바로 반환
    public void issueCoupon(Long couponId, Long memberId){
        Long code = redisTemplate.execute(couponIssueScript,
                List.of(String.format(META_KEY, couponId), String.format(MEMBERS_KEY, couponId)),
                String.valueOf(memberId),String.valueOf(System.currentTimeMillis()));
        switch(CouponIssueResult.of(code)){
            case SUCCESS -> publish(couponId, memberId);
            case DUPLICATE -> throw new DuplicateCouponException("이미 발급받은 쿠폰입니다.");
            case SOLD_OUT -> throw new CouponOutOfStockException("쿠폰이 모두 소진되었습니다.");
            case NOT_IN_PERIOD -> throw new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
            case NOT_FOUND -> throw new EntityNotFoundException("존재하지 않는 쿠폰입니다.");
        }
    }

    private void publish(Long couponId, Long memberId) {
        couponIssueProducer.send(new CouponIssueMessage(couponId, memberId))
                .whenComplete((result, ex) -> {
                    if(ex != null){
                        redisTemplate.opsForSet().remove(String.format(MEMBERS_KEY, couponId), String.valueOf(memberId));
                        log.error("쿠폰 발급 메시지 발행 실패, Redis 롤백 - couponId: {}, memberId: {}", couponId, memberId, ex);
                    }
                });
    }

    // 회원이 보유한 쿠폰 목록 조회
    @Transactional(readOnly = true)
    public List<CouponResponse> getMyCoupons(Long memberId){
        return memberCouponRepository.findByMemberId(memberId).stream()
            .map(CouponResponse::from)
            .toList();
    }
}
