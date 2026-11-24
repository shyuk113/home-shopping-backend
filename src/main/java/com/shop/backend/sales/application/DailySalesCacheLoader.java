package com.shop.backend.sales.application;

import com.shop.backend.sales.application.dto.CachedDailySales;
import com.shop.backend.sales.application.dto.DailySalesResponse;
import com.shop.backend.sales.infrastructure.DailySalesSummaryRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DailySalesCacheLoader {

    private final DailySalesSummaryRepository summaryRepository;

    @Cacheable(cacheNames = "sellerDashboard", key = "#sellerId + ':' + #from + ':' + #to")
    public CachedDailySales load(Long sellerId, LocalDate from, LocalDate to) {
        return new CachedDailySales(
                summaryRepository.findBySellerIdAndSalesDateBetweenOrderBySalesDate(sellerId, from, to)
                        .stream().map(DailySalesResponse::from).toList());
    }
}
