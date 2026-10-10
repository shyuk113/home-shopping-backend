package com.shop.backend.Item.application.dto.request;

import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.Item.domain.ItemStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ItemCreateRequestDto(
        @NotNull
        Long sellerId,
        @NotBlank(message = "상품명을 입력해주세요")
    String name,
        @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    int price,
        @Min(value = 0, message = "수량은 0 이상이어야 합니다")
    int quantity,
        String description,
        String imageUrl,
        ItemStatus status,
        ItemCategory category) {}
