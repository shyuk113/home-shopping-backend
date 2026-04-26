package com.shop.backend.Order.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
    @NotNull(message = "회원 ID를 입력해주세요.")
    Long memberId,
    @NotNull(message = "상품 ID를 입력해주세요.")
    Long itemId,
    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다.")
    int quantity,
    String address) {

}
