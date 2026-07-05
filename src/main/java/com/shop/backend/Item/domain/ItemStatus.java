package com.shop.backend.Item.domain;

public enum ItemStatus {

    SOLD_OUT("품절"),
    SELLING("판매중");

    private final String koreanName;
    ItemStatus(String koreanName){this.koreanName = koreanName;}
    public String getKoreanName() {return koreanName;}
}
