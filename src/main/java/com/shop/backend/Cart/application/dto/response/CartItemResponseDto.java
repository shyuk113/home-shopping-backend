package com.shop.backend.Cart.application.dto.response;

import com.shop.backend.Cart.domain.CartItem;

public record CartItemResponseDto(
        Long itemId,
        String itemName,
        int quantity,
        int price,
        int totalPrice
) {

    public static CartItemResponseDto from(CartItem cartItem){
        return new CartItemResponseDto(
            cartItem.getItem().getId(),
            cartItem.getItem().getName(),
            cartItem.getQuantity(),
            cartItem.getItem().getPrice(),
            cartItem.getQuantity() * cartItem.getItem().getPrice()
        );
    }
}
