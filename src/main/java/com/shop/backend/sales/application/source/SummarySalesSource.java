package com.shop.backend.sales.application.source;

import com.shop.backend.sales.application.dto.DailySalesResponse;
import com.shop.backend.sales.infrastructure.DailySalesSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dashboard.source", havingValue = "summary", matchIfMissing = true)
public class SummarySalesSource implements SalesSource {

    private final DailySalesSummaryRepository summaryRepository;

    @Override
    public List<DailySalesResponse> findDaily(Long sellerId, LocalDate from, LocalDate to) {
        return summaryRepository.findBySellerIdAndSalesDateBetweenOrderBySalesDate(sellerId, from, to)
                .stream().map(DailySalesResponse::from).toList();
    }
}
