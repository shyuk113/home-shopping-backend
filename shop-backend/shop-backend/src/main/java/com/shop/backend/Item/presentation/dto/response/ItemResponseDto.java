package com.shop.backend.Item.presentation.dto.response;

import com.shop.backend.Item.domain.model.ItemStatus;

public record ItemResponseDto(Long id, String name, String description, int price, int stockQuantity, ItemStatus status) {

}
