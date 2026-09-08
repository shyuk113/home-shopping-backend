package com.shop.backend.payment.presentation;

import com.shop.backend.payment.application.PaymentService;
import com.shop.backend.payment.application.dto.PaymentConfirmRequest;
import com.shop.backend.payment.application.dto.PaymentRequest;
import com.shop.backend.payment.application.dto.PaymentResponse;
import com.shop.backend.payment.domain.Payment;
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

    @PostMapping//결제 요청
    public ResponseEntity<PaymentResponse> ready(@Valid @RequestBody PaymentRequest request, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok().body(paymentService.ready(request.orderId(), request.method(), request.pgProvider(), userId));
    }

    @PostMapping("/{paymentKey}/confirm")// 결제 승인
    public ResponseEntity<Void> confirm(@PathVariable String paymentKey,
                                                   @Valid @RequestBody PaymentConfirmRequest request,
                                                   @AuthenticationPrincipal Long userId) {
        paymentService.confirm(paymentKey, request.amount(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{paymentKey}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentKey, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok().body(paymentService.getPayment(paymentKey, userId));
    }

    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String paymentKey, @AuthenticationPrincipal Long userId) {
        paymentService.cancel(paymentKey, userId);
        return ResponseEntity.noContent().build();
    }

}
