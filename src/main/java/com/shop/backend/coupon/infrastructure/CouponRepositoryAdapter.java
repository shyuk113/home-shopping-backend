package com.shop.backend.coupon.infrastructure;

import com.shop.backend.coupon.domain.Coupon;
import com.shop.backend.coupon.domain.CouponRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryAdapter implements CouponRepository {

    private final CouponJpaRepository jpa;

    @Override
    public Coupon save(Coupon coupon) {
        return jpa.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public void incrementIssuedQuantity(Long couponId) {
        jpa.incrementIssuedQuantity(couponId);
    }
}
