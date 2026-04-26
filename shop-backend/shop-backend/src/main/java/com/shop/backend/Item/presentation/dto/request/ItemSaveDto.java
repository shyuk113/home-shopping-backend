package com.shop.backend.Item.presentation.dto.request;

import com.shop.backend.Item.domain.model.Item;
import com.shop.backend.Item.domain.model.ItemStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ItemSaveDto(
    @NotBlank(message = "상품명을 입력해주세요")
    String name,
    @Min(value = 0, message = "가격은 0 이상이어야 합니다")
    int price,
    @Min(value = 0, message = "수량은 0 이상이어야 합니다")
    int quantity,
    String description,
    String imageUrl) {

    public Item toEntity(){
        return Item.builder()
            .name(name)
            .price(price)
            .quantity(quantity)
            .description(description)
            .imageUrl("default_image.png")
            .status(ItemStatus.SELLING)
            .build();
    }
}
