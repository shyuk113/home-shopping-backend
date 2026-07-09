package com.shop.backend.payment.application.dto;

import com.shop.backend.payment.domain.Payment;
import com.shop.backend.payment.domain.PaymentMethod;
import com.shop.backend.payment.domain.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentKey,
        PaymentMethod method,
        PaymentStatus status,
        int amount,
        LocalDateTime approvedAt
) {
    public static PaymentResponse from(Payment payment){
        return new PaymentResponse(
                payment.getPaymentKey(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getApprovedAt()
        );
    }
}
