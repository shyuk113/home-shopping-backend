package com.shop.backend.Cart.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddItemRequestDto(
    @NotNull(message = "상품 ID를 입력해주세요")
    Long itemId,
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    int quantity) {

}
