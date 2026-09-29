package com.shop.backend.coupon.application;

import com.shop.backend.coupon.domain.Coupon;
import com.shop.backend.coupon.domain.MemberCoupon;
import com.shop.backend.coupon.infrastructure.CouponRepository;
import com.shop.backend.coupon.infrastructure.MemberCouponRepository;
import com.shop.backend.global.exception.CouponOutOfStockException;
import com.shop.backend.global.exception.DuplicateCouponException;
import com.shop.backend.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//Redis 와 비교하기 위한 테스트
@Service
@RequiredArgsConstructor
public class CouponDbIssueService {

    private final CouponRepository couponRepository;
    private final MemberRepository memberRepository;
    private final MemberCouponRepository memberCouponRepository;

    @Transactional
    public void issueWithPessimisticLockCoupon(Long memberId, Long couponId) {
        Coupon coupon = couponRepository.findByIdForUpdate(couponId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 쿠폰입니다."));

        if(!coupon.isIssued()){
            throw new IllegalStateException("coupon issued");
        }
        if(memberCouponRepository.existsByMember_IdAndCoupon_Id(memberId, couponId)){
            throw new DuplicateCouponException("이미 밝브받은 쿠폰입니다.");
        }

        if(coupon.getIssuedQuantity() >= coupon.getTotalQuantity()){
            throw new CouponOutOfStockException("쿠폰이 모두 소진되었습니다.");
        }

        saveMemberCoupon(coupon, memberId);
        coupon.incrementIssuedQuantity();
    }

    private void saveMemberCoupon(Coupon coupon, Long memberId) {
        memberCouponRepository.save(MemberCoupon.builder()
                .member(memberRepository.getReferenceById(memberId))
                .coupon(coupon)
                .isUsed(false)
                .issuedAt(LocalDateTime.now())
                .build());
    }

    //원자적 update
    @Transactional
    public void issueWithAtomicUpdate(Long couponId, Long memberId){
        if(memberCouponRepository.existsByMember_IdAndCoupon_Id(memberId, couponId)){
            throw new DuplicateCouponException("이미 발급 받은 쿠폰입니다.");
        }

        int updated = couponRepository.increaseIssuedQuantityIfAvailable(couponId, LocalDateTime.now());
        if(updated==0){
            throw failureReason(couponId);
        }

        saveMemberCoupon(couponRepository.getReferenceById(couponId), memberId);
    }

    private RuntimeException failureReason(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId).orElse(null);
        if (coupon == null) return new EntityNotFoundException("존재하지 않는 쿠폰입니다.");
        if (!coupon.isIssued()) return new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
        return new CouponOutOfStockException("쿠폰이 모두 소진되었습니다.");
    }
}
