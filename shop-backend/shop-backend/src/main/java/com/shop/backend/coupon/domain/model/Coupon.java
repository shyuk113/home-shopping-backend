package com.shop.backend.coupon.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;

    private int discountAmount; // 할인 금액 (예: 1000원 할인)
    private int totalQuantity; // 총 발급 수량
    private int issuedQuantity; // 발급된 수량

    private LocalDateTime startTime; // 쿠폰 사용 가능 시작 시간
    private LocalDateTime endTime; // 쿠폰 사용 가능 종료 시간

    @Builder
    public Coupon(String name, int discountAmount, int totalQuantity, LocalDateTime startTime, LocalDateTime endTime) {
        this.name = name;
        this.discountAmount = discountAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0; // 초기 발급 수량은 0
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void incrementIssuedQuantity() {
        this.issuedQuantity++;
    }

    public boolean isIssued(){
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startTime) && now.isBefore(endTime);
    }
}
