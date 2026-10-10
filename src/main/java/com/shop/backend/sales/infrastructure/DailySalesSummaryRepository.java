package com.shop.backend.sales.infrastructure;

import com.shop.backend.sales.domain.DailySalesSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailySalesSummaryRepository extends JpaRepository<DailySalesSummary,Long> {

    List<DailySalesSummary> findBySellerIdAndSalesDateBetweenOrderBySalesDate(
            Long sellerId, LocalDate from, LocalDate to
    );
}
