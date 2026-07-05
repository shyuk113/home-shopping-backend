package com.shop.backend.coupon.application.dto.response;

import com.shop.backend.coupon.domain.MemberCoupon;
import java.time.LocalDateTime;
//회원이 보유한 쿠폰 정보
public record CouponResponse(
    Long memberCouponId,
    String CouponName,
    int discountAmount,
    boolean isUsed,
    LocalDateTime issuedAt
) {

    public static CouponResponse from(MemberCoupon memberCoupon){
        return new CouponResponse(
            memberCoupon.getId(),
            memberCoupon.getCoupon().getName(),
            memberCoupon.getCoupon().getDiscountAmount(),
            memberCoupon.isUsed(),
            memberCoupon.getIssuedAt()
        );
    }
}
