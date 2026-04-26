package com.shop.backend.coupon.domain.repsotiory;

import com.shop.backend.coupon.domain.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

}
