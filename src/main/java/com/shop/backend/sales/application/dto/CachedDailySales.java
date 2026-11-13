package com.shop.backend.sales.application.dto;

import java.util.List;

public record CachedDailySales(List<DailySalesResponse> days) {
}
