package com.shop.backend.Item.application.dto.response;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;

public record ItemResponseDto(Long id, String name, String description, int price, ItemStatus status) {

    public static ItemResponseDto from(Item item){
        return new ItemResponseDto(item.getId(),item.getName(),item.getDescription(),item.getPrice(),item.getStatus());
    }
}
