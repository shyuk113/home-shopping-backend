package com.shop.backend.coupon.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shop.backend.coupon.domain.Coupon;
import com.shop.backend.coupon.infrastructure.CouponRepository;
import com.shop.backend.coupon.infrastructure.MemberCouponRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class CouponServiceTest {

    private static final int COUPON_STOCK = 100;
    private static final int THREAD_COUNT = 500;

    @Autowired
    private CouponService couponService;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberCouponRepository memberCouponRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void issuedCoupon() throws InterruptedException {
        Coupon coupon = couponRepository.save(Coupon.builder()
            .name("선착순 100명 쿠폰")
            .discountAmount(1000)
            .totalQuantity(COUPON_STOCK)
            .startTime(LocalDateTime.now().minusMinutes(1))
            .endTime(LocalDateTime.now().plusMinutes(10))
            .build());

        // 이전 테스트 실행에서 남은 Redis 데이터 정리 (H2는 매번 ID가 1부터 리셋되지만 Redis는 안 지워짐)
        redisTemplate.delete(String.format("coupon:%d:members", coupon.getId()));
        redisTemplate.delete(String.format("coupon:%d:count", coupon.getId()));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        Map<String, AtomicInteger> failReasons = new ConcurrentHashMap<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Long memberId = createMember("010" + String.format("%08d", i)).getId();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 모든 스레드가 동시에 출발하도록 대기
                    couponService.issuedCoupon(coupon.getId(), memberId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    String key = e.getClass().getSimpleName() + ": " + e.getMessage();
                    failReasons.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        System.out.println("=== 실패 원인 분포 ===");
        failReasons.forEach((reason, count) -> System.out.println(count + "건 - " + reason));

        long savedCount = memberCouponRepository.findAll().stream()
            .filter(mc -> mc.getCoupon().getId().equals(coupon.getId()))
            .count();
        Coupon result = couponRepository.findById(coupon.getId()).orElseThrow();

        assertEquals(COUPON_STOCK, successCount.get(), "성공 건수는 정확히 재고만큼이어야 함");
        assertEquals(THREAD_COUNT - COUPON_STOCK, failCount.get(), "나머지는 중복/소진으로 실패해야 함");
        assertEquals(COUPON_STOCK, savedCount, "DB에 실제 저장된 MemberCoupon 건수");
        assertEquals(COUPON_STOCK, result.getIssuedQuantity(), "Coupon.issuedQuantity 통계 필드 (lost update 있으면 여기서 깨짐)");
    }

    private Member createMember(String phone) {
        Member member = Member.builder()
            .name("tester")
            .phone(phone)
            .email(phone + "@test.com")
            .password("password")
            .role(Member.Role.USER)
            .address("서울")
            .build();
        return memberRepository.save(member);
    }
}
