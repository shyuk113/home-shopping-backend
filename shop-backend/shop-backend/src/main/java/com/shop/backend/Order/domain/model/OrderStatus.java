package com.shop.backend.Order.domain.model;

public enum OrderStatus {
    ORDER("주문 완료")
    ,CANCEL("주문 취소");
    private final String koreanName;
    OrderStatus(String koreanName){this.koreanName = koreanName;}
    public String getKoreanName() {return koreanName;}
}
