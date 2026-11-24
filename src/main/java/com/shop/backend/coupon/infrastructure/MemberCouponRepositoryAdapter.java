package com.shop.backend.coupon.infrastructure;

import com.shop.backend.coupon.domain.MemberCoupon;
import com.shop.backend.coupon.domain.MemberCouponRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemberCouponRepositoryAdapter implements MemberCouponRepository {

    private final MemberCouponJpaRepository jpa;

    @Override
    public MemberCoupon save(MemberCoupon memberCoupon) {
        return jpa.save(memberCoupon);
    }

    @Override
    public List<MemberCoupon> findAll() {
        return jpa.findAll();
    }

    @Override
    public List<MemberCoupon> findByMemberId(Long memberId) {
        return jpa.findByMemberId(memberId);
    }
}
