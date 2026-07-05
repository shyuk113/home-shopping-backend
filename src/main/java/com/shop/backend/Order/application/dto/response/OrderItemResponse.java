package com.shop.backend.Order.application.dto.response;

import com.shop.backend.Order.domain.OrderItem;
//주문 내 개별 상품 정보를 담는 응답 dto
public record OrderItemResponse(
    Long itemId,
    String itemName,
    int orderPrice,
    int quantity,
    int totalPrice
) {

    public static OrderItemResponse from(OrderItem orderItem){
        return new OrderItemResponse(
            orderItem.getItem().getId(),
            orderItem.getItem().getName(),
            orderItem.getOrderPrice(),
            orderItem.getQuantity(),
            orderItem.getTotalPrice()
        );
    }
}
