package com.shop.backend.Item.presentation.dto.request;

import lombok.Getter;


public record ItemUpdateDto(Long itemId, String itemName, int price, int quantity, String description, String imageUrl) {

}
