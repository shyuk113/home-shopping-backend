package com.shop.backend.Cart.application.dto.request;

public record CartDeleteItemRequestDto(Long memberId,Long itemId,int quantity) {

}
