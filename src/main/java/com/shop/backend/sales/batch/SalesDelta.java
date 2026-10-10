package com.shop.backend.sales.batch;

import java.time.LocalDate;

public record SalesDelta(
        long sellerId,
        LocalDate salesDate,
        int orderCount,
        int itemQuantity,
        long salesAmount,
        int cancelCount,
        long cancelAmount
) {
    public static SalesDelta from(SalesRow row){
        return switch (row.status()){
            case PAID -> new SalesDelta(row.sellerId(), row.salesDate(), 1, row.quantity(),
                    row.amount(),0,0);
            case CANCEL -> new SalesDelta(row.sellerId(), row.salesDate(), 0,0,0,1, row.amount());
            default -> throw new IllegalArgumentException("집계 대상이 아닌 주문 상태: " + row.status());
        };
    }
    public SalesDelta plus(SalesDelta other){
        return new SalesDelta(sellerId, salesDate,
                orderCount + other.orderCount,
                itemQuantity + other.itemQuantity,
                salesAmount + other.salesAmount,
                cancelCount + other.cancelCount,
                cancelAmount + other.cancelAmount);
    }
}
