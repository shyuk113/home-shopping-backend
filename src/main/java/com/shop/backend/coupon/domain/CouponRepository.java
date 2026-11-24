package com.shop.backend.coupon.domain;

import java.util.Optional;

/**
 * Coupon 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 CouponRepositoryAdapter 가 담당한다.
 */
public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    void incrementIssuedQuantity(Long couponId);
}
