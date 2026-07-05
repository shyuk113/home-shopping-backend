package com.shop.backend.cart.application.dto.request;

public record CartDeleteItemRequestDto(Long memberId,Long itemId,int quantity) {

}
