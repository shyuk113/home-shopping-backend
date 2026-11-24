package com.shop.backend.sales.infrastructure;

import com.shop.backend.sales.domain.DailySalesSummary;
import com.shop.backend.sales.domain.DailySalesSummaryRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailySalesSummaryRepositoryAdapter implements DailySalesSummaryRepository {

    private final DailySalesSummaryJpaRepository jpa;

    @Override
    public List<DailySalesSummary> findBySellerIdAndSalesDateBetweenOrderBySalesDate(Long sellerId, LocalDate from, LocalDate to) {
        return jpa.findBySellerIdAndSalesDateBetweenOrderBySalesDate(sellerId, from, to);
    }
}
