package com.shop.backend.payment.presentation;

import com.shop.backend.payment.application.PaymentService;
import com.shop.backend.payment.application.dto.PaymentConfirmRequest;
import com.shop.backend.payment.application.dto.PaymentRequest;
import com.shop.backend.payment.application.dto.PaymentResponse;
import com.shop.backend.payment.domain.Payment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping//결제 요청
    public ResponseEntity<PaymentResponse> ready(@Valid @RequestBody PaymentRequest request) {
        Payment payment = paymentService.ready(request.orderId(), request.method(), request.pgProvider());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @PostMapping("/{paymentKey}/confirm")// 결제 승인
    public ResponseEntity<PaymentResponse> confirm(@PathVariable String paymentKey,
                                                   @Valid @RequestBody PaymentConfirmRequest request){
        paymentService.confirm(paymentKey, request.amount());
        Payment payment = paymentService.getPayment(paymentKey);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @GetMapping("/{paymentKey}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentKey){
        return ResponseEntity.ok(PaymentResponse.from(paymentService.getPayment(paymentKey)));
    }

    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String paymentKey){
        paymentService.cancel(paymentKey);
        return ResponseEntity.ok().build();
    }

}
