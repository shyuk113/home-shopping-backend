package com.shop.backend.coupon.infrastructure;

import com.shop.backend.coupon.domain.MemberCoupon;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    List<MemberCoupon> findByMemberId(Long memberId);

    boolean existsByMember_IdAndCoupon_Id(Long memberId, Long couponId);

}
