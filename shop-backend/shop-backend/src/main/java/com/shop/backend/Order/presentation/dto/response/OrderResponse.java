package com.shop.backend.Order.presentation.dto.response;

import com.shop.backend.Order.domain.model.Order;
import com.shop.backend.Order.domain.model.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

//주문 전체 응답 DTO
public record OrderResponse(
    Long orderId,
    OrderStatus status,
    LocalDateTime orderTime,
    List<OrderItemResponse> orderItems,
    int totalPrice
) {

    public static OrderResponse from(Order order){
        List<OrderItemResponse> items = order.getOrderItems().stream()
            .map(OrderItemResponse::from)
            .toList();
        int totalPrice = items.stream()
            .mapToInt(OrderItemResponse::totalPrice)
            .sum();
        return new OrderResponse(
            order.getId(),
            order.getStatus(),
            order.getOrderTime(),
            items,
            totalPrice
        );
    }
}
