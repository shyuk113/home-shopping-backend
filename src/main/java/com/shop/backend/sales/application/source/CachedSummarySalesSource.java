package com.shop.backend.sales.application.source;

import com.shop.backend.sales.application.DailySalesCacheLoader;
import com.shop.backend.sales.application.dto.DailySalesResponse;
import com.shop.backend.sales.infrastructure.DailySalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dashboard.source", havingValue = "cache")
public class CachedSummarySalesSource implements SalesSource {

    private final DailySalesCacheLoader dailySalesCacheLoader;

    @Override
    public List<DailySalesResponse> findDaily(Long sellerId, LocalDate from, LocalDate to) {
        return dailySalesCacheLoader.load(sellerId, from, to).days();
    }
}
