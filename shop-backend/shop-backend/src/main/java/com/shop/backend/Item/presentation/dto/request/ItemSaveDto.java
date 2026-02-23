package com.shop.backend.Item.presentation.dto.request;

import com.shop.backend.Item.domain.model.Item;
import com.shop.backend.Item.domain.model.ItemStatus;

public record ItemSaveDto(String name, int price, int quantity, String description, String imageUrl) {

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
