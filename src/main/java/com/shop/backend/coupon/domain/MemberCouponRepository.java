package com.shop.backend.coupon.domain;

import java.util.List;

/**
 * MemberCoupon 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 MemberCouponRepositoryAdapter 가 담당한다.
 */
public interface MemberCouponRepository {

    MemberCoupon save(MemberCoupon memberCoupon);

    List<MemberCoupon> findAll();

    List<MemberCoupon> findByMemberId(Long memberId);
}
