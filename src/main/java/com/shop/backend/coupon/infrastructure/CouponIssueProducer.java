package com.shop.backend.coupon.infrastructure;

import com.shop.backend.coupon.application.CouponIssueMessage;
import lombok.RequiredArgsConstructor;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class CouponIssueProducer {

    public static final String TOPIC = "coupon-issue";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public CompletableFuture<SendResult<String, String>> send(CouponIssueMessage message) {
        String payload = jsonMapper.writeValueAsString(message);
        return kafkaTemplate.send(TOPIC, String.valueOf(message.memberId()), payload);
    }
}
