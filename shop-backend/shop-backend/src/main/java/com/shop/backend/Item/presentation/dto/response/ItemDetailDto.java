package com.shop.backend.Item.presentation.dto.response;

import com.shop.backend.Item.domain.model.ItemStatus;

public record ItemDetailDto(Long id, String name, String description, int price, ItemStatus status) {

}
