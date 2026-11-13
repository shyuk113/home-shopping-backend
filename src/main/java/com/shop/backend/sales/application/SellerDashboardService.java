package com.shop.backend.sales.application;

import com.shop.backend.sales.application.dto.SellerDashboardResponse;
import com.shop.backend.sales.application.source.SalesSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerDashboardService {

    private static final int MAX_DAYS = 92;
    private final SalesSource salesSource;

    public SellerDashboardResponse getDashboard(Long sellerId, LocalDate from, LocalDate to) {
        if(from.isAfter(to) || ChronoUnit.DAYS.between(from,to) >=  MAX_DAYS) {
            throw new IllegalArgumentException("조회 기간은 1~" +  MAX_DAYS+ "일 이어야 합니다.");
        }
        return SellerDashboardResponse.of(sellerId, from, to, salesSource.findDaily(sellerId, from, to));
    }
}
