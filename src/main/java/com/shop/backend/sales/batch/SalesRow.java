package com.shop.backend.sales.batch;

import com.shop.backend.order.domain.OrderStatus;

import java.time.LocalDate;
/**
 * Reader가 읽는 한 행: "주문 1건이 판매자 1명에게 만든 매출".
 * 주문 안에 같은 판매자의 상품이 여러 개면 수량/금액을 합쳐 한 행으로 읽는다.
 */
public record SalesRow(
        long orderId,
        long sellerId,
        LocalDate salesDate,
        OrderStatus status,
        int quantity,
        long amount
) {
}
