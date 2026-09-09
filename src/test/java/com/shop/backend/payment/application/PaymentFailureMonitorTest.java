package com.shop.backend.payment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PaymentFailureMonitorTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String,String> valueOperations;

    private PaymentFailureMonitor monitor;

    @BeforeEach
    void setUp(){
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        monitor = new PaymentFailureMonitor(redisTemplate);
        ReflectionTestUtils.setField(monitor, "threshold", 3);
        ReflectionTestUtils.setField(monitor, "windowSeconds", 60L);
        ReflectionTestUtils.setField(monitor, "cooldownSeconds", 300L);
    }

    @Test
    @DisplayName("임계치 미만이면 쿨다운을 시도하지 않음")
    void not_cooldown(){
        //count(2) >= threshold(3) 조건이 거짓이 되게 세팅
        when(valueOperations.increment(anyString())).thenReturn(2L);
        monitor.recordFailure("재고 부족");
        verify(valueOperations,never()).setIfAbsent(anyString(),anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("임계치 이상이면 쿨다운키 선점을 시도")
    void try_cooldown(){
        when(valueOperations.increment(anyString())).thenReturn(5L);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        monitor.recordFailure("재고 부족");
        verify(valueOperations).setIfAbsent(eq("payment:fail:alert:cooldown"),anyString(),eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("이미 쿨다운 중이면 스킵")
    void if_cooldownThenSkip(){
        when(valueOperations.increment(anyString())).thenReturn(10L);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        monitor.recordFailure("결제 금액 불일치");
        verify(valueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
}