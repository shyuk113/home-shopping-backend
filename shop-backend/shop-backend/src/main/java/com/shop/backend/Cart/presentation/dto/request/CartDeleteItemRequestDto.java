package com.shop.backend.Cart.presentation.dto.request;

public record CartDeleteItemRequestDto(Long memberId,Long itemId,int quantity) {

}
