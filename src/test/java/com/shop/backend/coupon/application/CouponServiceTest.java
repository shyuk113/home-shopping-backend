package com.shop.backend.coupon.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shop.backend.coupon.application.dto.request.CouponCreateRequestDto;
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
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

// Redis(6379), Kafka(9092)가 떠 있어야 한다.
// 같은 consumer group(coupon-issue)을 쓰는 로컬 애플리케이션이 실행 중이면 메시지를 그쪽이 가져가므로 종료 후 실행할 것.
@SpringBootTest
class CouponServiceTest {

    private static final int COUPON_STOCK = 100;
    private static final int THREAD_COUNT = 500;
    private static final long CONSUME_TIMEOUT_MILLIS = 30_000;

    @Autowired
    private CouponService couponService;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private MemberCouponRepository memberCouponRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long couponId;

    @AfterEach
    void cleanUpRedis() {
        if (couponId != null) {
            redisTemplate.delete(metaKey(couponId));
            redisTemplate.delete(membersKey(couponId));
        }
    }

    @Test
    void issuedCoupon() throws InterruptedException {
        // createCoupon은 DB 커밋 후 Redis에 coupon:{id}:meta를 등록한다 (Lua 스크립트의 발급 판정 기준)
        couponId = couponService.createCoupon(new CouponCreateRequestDto(
            "선착순 100명 쿠폰",
            1000,
            COUPON_STOCK,
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now().plusMinutes(10)));

        // H2는 실행마다 ID가 1부터 리셋되지만 Redis는 남아 있으므로, 이전 실행의 발급자 Set을 정리
        redisTemplate.delete(membersKey(couponId));

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
                    couponService.issueCoupon(couponId, memberId);
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

        // 1) Redis 판정: 동기적으로 결정된다
        assertEquals(COUPON_STOCK, successCount.get(), "성공 건수는 정확히 재고만큼이어야 함");
        assertEquals(THREAD_COUNT - COUPON_STOCK, failCount.get(), "나머지는 소진으로 실패해야 함");
        assertEquals(COUPON_STOCK, redisTemplate.opsForSet().size(membersKey(couponId)), "Redis 발급자 Set 크기");

        // 2) DB 저장: Kafka consumer가 비동기로 처리하므로 반영될 때까지 대기
        long savedCount = awaitCount(this::countSavedMemberCoupons, COUPON_STOCK);
        Coupon result = couponRepository.findById(couponId).orElseThrow();

        assertEquals(COUPON_STOCK, savedCount, "DB에 실제 저장된 MemberCoupon 건수");
        assertEquals(COUPON_STOCK, result.getIssuedQuantity(), "Coupon.issuedQuantity 통계 필드 (lost update 있으면 여기서 깨짐)");
    }

    private long countSavedMemberCoupons() {
        return memberCouponRepository.findAll().stream()
            .filter(mc -> mc.getCoupon().getId().equals(couponId))
            .count();
    }

    // 기대 건수에 도달하거나 타임아웃이 날 때까지 폴링하고 마지막 값을 반환
    private long awaitCount(LongSupplier counter, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + CONSUME_TIMEOUT_MILLIS;
        long count = counter.getAsLong();
        while (count < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            count = counter.getAsLong();
        }
        return count;
    }

    private static String metaKey(Long couponId) {
        return String.format("coupon:{%d}:meta", couponId);
    }

    private static String membersKey(Long couponId) {
        return String.format("coupon:{%d}:members", couponId);
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
