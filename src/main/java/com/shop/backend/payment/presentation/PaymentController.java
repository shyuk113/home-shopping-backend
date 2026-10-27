package com.shop.backend.payment.presentation;

import com.shop.backend.member.domain.Member;
import com.shop.backend.payment.application.PaymentConfirmFacade;
import com.shop.backend.payment.application.PaymentService;
import com.shop.backend.payment.application.dto.PaymentConfirmRequest;
import com.shop.backend.payment.application.dto.PaymentRequest;
import com.shop.backend.payment.application.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentConfirmFacade paymentConfirmFacade;

    @PostMapping//결제 요청
    public ResponseEntity<PaymentResponse> ready(@Valid @RequestBody PaymentRequest request, @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok().body(paymentService.ready(request.orderId(), request.method(), request.pgProvider(), member.getId()));
    }

    @PostMapping("/{paymentKey}/confirm")// 결제 승인
    public ResponseEntity<Void> confirm(@PathVariable String paymentKey,
                                                   @Valid @RequestBody PaymentConfirmRequest request,
                                                   @AuthenticationPrincipal Member member) {
        paymentConfirmFacade.confirm(paymentKey, request.amount(), member.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{paymentKey}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentKey, @AuthenticationPrincipal Member member) {
        return ResponseEntity.ok().body(paymentService.getPayment(paymentKey, member.getId()));
    }

    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String paymentKey, @AuthenticationPrincipal Member member) {
        paymentService.cancel(paymentKey, member.getId());
        return ResponseEntity.noContent().build();
    }

}
