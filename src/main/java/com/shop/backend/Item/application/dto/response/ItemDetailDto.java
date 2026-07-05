package com.shop.backend.Item.application.dto.response;

import com.shop.backend.Item.domain.ItemStatus;

public record ItemDetailDto(Long id, String name, String description, int price, ItemStatus status) {

}
