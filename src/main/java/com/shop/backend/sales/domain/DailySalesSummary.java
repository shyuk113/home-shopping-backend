package com.shop.backend.sales.domain;

import com.shop.backend.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="daily_sales_summary", uniqueConstraints = @UniqueConstraint(name = "uk_daily_sales_seller_date", columnNames =
        {"seller_id", "sales_date"}),
indexes = @Index(name= "idx_daily_sales_date", columnList = "sales_date"))
public class DailySalesSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate salesDate;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private int orderCount;

    @Column(nullable = false)
    private int itemQuantity;

    @Column(nullable = false)
    private long salesAmount;

    @Column(nullable = false)
    private int cancelCount;

    @Column(nullable = false)
    private long cancelAmount;

    public long getNetSalesAmount(){
        return salesAmount - cancelAmount;
    }
}
