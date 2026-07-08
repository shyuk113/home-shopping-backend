package com.shop.backend.payment.application.dto;

import com.shop.backend.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull
        Long orderId,
        @NotNull
        PaymentMethod method,
        String pgProvider) {
}
