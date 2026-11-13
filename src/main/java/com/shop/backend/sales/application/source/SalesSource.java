package com.shop.backend.sales.application.source;

import com.shop.backend.sales.application.dto.DailySalesResponse;

import java.time.LocalDate;
import java.util.List;

public interface SalesSource {

    List<DailySalesResponse> findDaily(Long sellerId, LocalDate from, LocalDate to);
}
