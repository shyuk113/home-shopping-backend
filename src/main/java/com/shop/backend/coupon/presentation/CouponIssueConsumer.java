package com.shop.backend.coupon.presentation;

import com.shop.backend.coupon.application.CouponIssueMessage;
import com.shop.backend.coupon.application.CouponIssuedService;
import com.shop.backend.coupon.infrastructure.CouponIssueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponIssueConsumer {

    private final CouponIssuedService  couponIssuedService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = CouponIssueProducer.TOPIC, groupId = "coupon-issue")
    public void consume(String payload){
        CouponIssueMessage message = jsonMapper.readValue(payload, CouponIssueMessage.class);
        try{
            couponIssuedService.saveIssuedCoupon(message.couponId(), message.memberId());
        } catch(DataIntegrityViolationException e){
            // Kafka는 최소 1회 전달이라 같은 메시지가 다시 올 수 있음.
            // (member_id, coupon_id) 유니크 제약에 걸리면 이미 저장된 요청이므로 무시
            log.info("이미 처리된 발급 메세지 - couponId: {}, memberId: {}", message.couponId(), message.memberId());
        }
    }
}
