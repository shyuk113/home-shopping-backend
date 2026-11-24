package com.shop.backend.sales.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * DailySalesSummary 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 DailySalesSummaryRepositoryAdapter 가 담당한다.
 */
public interface DailySalesSummaryRepository {

    List<DailySalesSummary> findBySellerIdAndSalesDateBetweenOrderBySalesDate(Long sellerId, LocalDate from, LocalDate to);
}
