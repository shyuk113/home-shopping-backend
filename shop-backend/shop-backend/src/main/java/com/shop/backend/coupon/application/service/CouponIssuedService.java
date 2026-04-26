package com.shop.backend.coupon.application.service;

import com.shop.backend.Member.domain.model.Member;
import com.shop.backend.Member.domain.repository.MemberRepository;
import com.shop.backend.coupon.domain.model.Coupon;
import com.shop.backend.coupon.domain.model.MemberCoupon;
import com.shop.backend.coupon.domain.repsotiory.CouponRepository;
import com.shop.backend.coupon.domain.repsotiory.MemberCouponRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssuedService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveIssuedCoupon(Long couponId, Long memberId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(()-> new EntityNotFoundException("존재하지 않는 쿠폰입니다."));
        Member member = memberRepository.findById(memberId)
            .orElseThrow(()-> new EntityNotFoundException("존재하지 않는 회원입니다."));

        memberCouponRepository.save(MemberCoupon.builder()
            .member(member)
            .coupon(coupon)
        .build());

        coupon.incrementIssuedQuantity();
    }
}
