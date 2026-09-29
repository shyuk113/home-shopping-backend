package com.shop.backend.coupon.presentation;

import com.shop.backend.coupon.application.CouponDbIssueService;
import com.shop.backend.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponTestController {

    private final CouponDbIssueService couponDbIssueService;

    @PostMapping("/{couponId}/issue/pessimistic")
    public ResponseEntity<Void> issueWithPessimisticLock(@PathVariable Long couponId,
                                                         @AuthenticationPrincipal Member member) {
        couponDbIssueService.issueWithPessimisticLockCoupon(member.getId(), couponId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{couponId}/issue/atomic")
    public ResponseEntity<Void> issueWithAtomicUpdate(@PathVariable Long couponId, @AuthenticationPrincipal Member member) {
        couponDbIssueService.issueWithAtomicUpdate(couponId, member.getId());
        return ResponseEntity.ok().build();
    }
}
