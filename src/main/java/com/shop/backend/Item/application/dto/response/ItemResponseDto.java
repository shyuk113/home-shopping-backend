package com.shop.backend.Item.application.dto.response;

import com.shop.backend.Item.domain.ItemStatus;

public record ItemResponseDto(Long id, String name, String description, int price, int stockQuantity, ItemStatus status) {

}
