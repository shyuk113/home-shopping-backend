package com.shop.backend.sales.application.dto;

import com.shop.backend.sales.domain.DailySalesSummary;

import java.time.LocalDate;

public record DailySalesResponse(LocalDate salesDate,
                                 int orderCount,
                                 int itemQuantity,
                                 long salesAmount,
                                 int cancelCount,
                                 long cancelAmount) {
    public static DailySalesResponse from(DailySalesSummary s){
        return new DailySalesResponse(s.getSalesDate(), s.getOrderCount(),
                s.getItemQuantity(), s.getSalesAmount(),
                s.getCancelCount(), s.getCancelAmount());
    }
}
