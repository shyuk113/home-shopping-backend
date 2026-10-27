package com.shop.backend.payment.application;

import com.shop.backend.global.exception.PaymentFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentConfirmFacade {

    private final PaymentService paymentService;
    private final PaymentFailureMonitor paymentFailureMonitor;

    public void confirm(String paymentKey, int approvedAmount, Long userId) {
        try{
            paymentService.confirm(paymentKey, approvedAmount, userId);
        } catch (PaymentFailedException e) {
            paymentService.markFailed(paymentKey, e.getReason());
            paymentFailureMonitor.recordFailure(e.getReason());
            throw e;
        }
    }
}
