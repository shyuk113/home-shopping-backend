package com.shop.backend.coupon.presentation;

import com.shop.backend.Member.domain.Member;
import com.shop.backend.coupon.application.CouponService;
import com.shop.backend.coupon.application.dto.request.CouponCreateRequestDto;
import com.shop.backend.coupon.application.dto.response.CouponResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping //쿠폰 생성
    public ResponseEntity<Long> createCoupon(@Valid @RequestBody CouponCreateRequestDto request){
        return ResponseEntity.ok(couponService.createCoupon(request));
    }

    @PostMapping("/{couponId}/issue") //쿠폰 발급
    public ResponseEntity<?> issueCoupon(@PathVariable Long couponId, @AuthenticationPrincipal Member member){
        couponService.issuedCoupon(couponId, member.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my") //내 쿠폰 조회
    public ResponseEntity<List<CouponResponse>> getMyCoupon(@AuthenticationPrincipal Member member){
        return ResponseEntity.ok(couponService.getMyCoupons(member.getId()));
    }
}
