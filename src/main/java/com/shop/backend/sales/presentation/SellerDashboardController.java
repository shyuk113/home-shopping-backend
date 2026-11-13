package com.shop.backend.sales.presentation;

import com.shop.backend.sales.application.SellerDashboardService;
import com.shop.backend.sales.application.dto.SellerDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RequestMapping("/api/admin/sellers")
@RestController
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @GetMapping("/{sellerId}/dashboard")
    public ResponseEntity<SellerDashboardResponse> getDashboard(
            @PathVariable Long sellerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate to
            ){
        return ResponseEntity.ok(sellerDashboardService.getDashboard(sellerId, from, to));
    }
}
