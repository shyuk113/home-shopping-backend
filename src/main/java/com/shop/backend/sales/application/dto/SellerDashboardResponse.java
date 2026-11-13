package com.shop.backend.sales.application.dto;


import java.time.LocalDate;
import java.util.List;

public record SellerDashboardResponse(Long sellerId,
                                      LocalDate from,
                                      LocalDate to,
                                      List<DailySalesResponse> days,
                                      long totalSalesAmount,
                                      int totalOrderCount) {
    public static SellerDashboardResponse of(Long sellerId, LocalDate from,
                                             LocalDate to,
                                             List<DailySalesResponse> days){
        return new SellerDashboardResponse(sellerId, from, to, days,
                days.stream().mapToLong(DailySalesResponse::salesAmount).sum(),
                days.stream().mapToInt(DailySalesResponse::orderCount).sum());
    }
}
