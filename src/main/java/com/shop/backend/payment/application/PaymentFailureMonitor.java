package com.shop.backend.payment.application;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailureMonitor {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${payment.failure-alert.threshold:5}")
    private int threshold;

    @Value("${payment.failure-alert.window-seconds:60}")
    private long windowSeconds;

    @Value("${payment.failure-alert.cooldown-seconds:300}")
    private long cooldownSeconds;

    private static final String COUNT_KEY_PREFIX = "payment:fail:count:";
    private static final String COOLDOWN_KEY = "payment:fail:alert:cooldown";

    public void recordFailure(String reason){
        long windowBucket = Instant.now().getEpochSecond() / windowSeconds;
        String countKey = COUNT_KEY_PREFIX + windowBucket;

        Long count = redisTemplate.opsForValue().increment(countKey);

        if(count != null && count ==1L){
            redisTemplate.expire(countKey, Duration.ofSeconds(windowSeconds));
        }

        if(count != null && count >= threshold){
            tryAlert(reason,count);
        }
    }

    private void tryAlert(String reason, long count){
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_KEY, "1", Duration.ofSeconds(cooldownSeconds));

        if(Boolean.TRUE.equals(acquired)){
            log.error("[결제 실패 급증 감지] 최근 {}초간 {}회 실패 (임계치 {}회) - 마지막 사유:{}",
                    windowSeconds, count, threshold,reason);
        }
    }
}
