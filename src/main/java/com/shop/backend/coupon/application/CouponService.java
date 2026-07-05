package com.shop.backend.coupon.application;

import com.shop.backend.coupon.domain.Coupon;
import com.shop.backend.coupon.infrastructure.CouponRepository;
import com.shop.backend.coupon.infrastructure.MemberCouponRepository;
import com.shop.backend.coupon.application.dto.reqeust.CouponCreateRequestDto;
import com.shop.backend.coupon.application.dto.response.CouponResponse;
import com.shop.backend.global.exception.CouponOutOfStockException;
import com.shop.backend.global.exception.DuplicateCouponException;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final CouponIssuedService couponIssuedService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String COUPON_COUNT_KEY = "coupon:%d:count";
    private static final String COUPON_MEMBER_KEY = "coupon:%d:members";

    @Transactional
    public Long createCoupon(CouponCreateRequestDto request){
        Coupon coupon = Coupon.builder()
            .name(request.name())
            .discountAmount(request.discountAmount())
            .totalQuantity(request.totalQuantity())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .build();
        return couponRepository.save(coupon).getId();
    }

    public void issuedCoupon(Long couponId, Long memberId){
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 쿠폰입니다."));

        // 쿠폰 발급 기간 체크
        if(!coupon.isIssued()){
            throw new IllegalStateException("쿠폰 발릅 기간이 아닙니다");
        }

        String memberKey = String.format(COUPON_MEMBER_KEY, couponId);
        String countKey = String.format(COUPON_COUNT_KEY, couponId);

        // 중복 발급 체크
        Long added = redisTemplate.opsForSet().add(memberKey, String.valueOf(memberId));
        if(added == null || added ==0){
            throw new DuplicateCouponException("이미 발급받은 쿠폰입니다");
        }

        // 발급 수량 체크 및 증가
        Long count = redisTemplate.opsForValue().increment(countKey);
        if(count == null ||count > coupon.getTotalQuantity()){
            redisTemplate.opsForSet().remove(memberKey, String.valueOf(memberId));
            redisTemplate.opsForValue().increment(countKey);
            throw new CouponOutOfStockException("쿠폰이 모두 소진되었습니다");
        }

        // DB에 발급 정보 저장
        try{
            couponIssuedService.saveIssuedCoupon(couponId, memberId);
        } catch(Exception e){
            redisTemplate.opsForSet().remove(memberKey, String.valueOf(memberId));
            redisTemplate.opsForValue().decrement(countKey);
            log.error("쿠폰 발급 DB 저장 실패 - couponId: {}, memberId: {}", couponId, memberId);
            throw new IllegalStateException("쿠폰 발급 중 오류가 발생했습니다.");
        }
    }
    // 회원이 보유한 쿠폰 목록 조회
    @Transactional(readOnly = true)
    public List<CouponResponse> getMyCoupons(Long memberId){
        return memberCouponRepository.findByMemberId(memberId).stream()
            .map(CouponResponse::from)
            .toList();
    }
}
