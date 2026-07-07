package com.shop.backend.coupon.infrastructure;

import com.shop.backend.coupon.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.issuedQuantity = c.issuedQuantity + 1 WHERE c.id = :couponId")
    void incrementIssuedQuantity(@Param("couponId") Long couponId);
}
