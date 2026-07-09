package com.shop.backend.payment.application.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentConfirmRequest(
        @NotNull
        Integer amount
) {
}
