package com.shop.backend.Item.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;


public record ItemUpdateDto(
    Long itemId,

    @NotBlank(message = "상품명을 입력해주세요")
    String itemName,
    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    int price,
    @Min(value = 0, message = "수량은 0 이상이어야 합니다")
    int quantity,
    String description, String imageUrl) {

}
