package com.shop.backend.order.domain;

public enum OrderStatus {
    PENDING("결제 대기"),
    PAID("결제 완료"),
    CANCEL("주문 취소"),
    FAILED("결제 실패");
    private final String koreanName;
    OrderStatus(String koreanName){this.koreanName = koreanName;}
    public String getKoreanName() {return koreanName;}
}
