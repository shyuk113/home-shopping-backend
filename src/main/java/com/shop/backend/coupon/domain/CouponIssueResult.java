package com.shop.backend.coupon.domain;

import java.util.Arrays;

public enum CouponIssueResult {

    SUCCESS(1),
    SOLD_OUT(0),
    DUPLICATE(-1),
    NOT_IN_PERIOD(-2),
    NOT_FOUND(-2);

    private final long code;
    CouponIssueResult(long code){
        this.code = code;
    }

    public static CouponIssueResult of(long code){
        return Arrays.stream(values()).filter(r->r.code==code).findFirst()
                .orElseThrow(()->new IllegalStateException("알 수 없는 Lua 결과: " + code));
    }
}
