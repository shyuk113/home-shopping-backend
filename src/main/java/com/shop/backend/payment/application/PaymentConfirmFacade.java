package com.shop.backend.payment.application;

import com.shop.backend.global.exception.PaymentFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentConfirmFacade {

    private final PaymentService paymentService;
    private final PaymentFailureMonitor paymentFailureMonitor;
    private static final int MAX_ATTEMPT = 30;

    public void confirm(String paymentKey, int approvedAmount, Long userId) {

        for(int attempt = 1; ; attempt++){
            try{
                paymentService.confirm(paymentKey, approvedAmount, userId);
                return;
            } catch (ObjectOptimisticLockingFailureException e){
                if(attempt >= MAX_ATTEMPT){
                    throw e;
                }
                sleepWithJitter(attempt);
            } catch(PaymentFailedException e){
                paymentService.markFailed(paymentKey, e.getReason());
                paymentFailureMonitor.recordFailure(e.getReason());
                throw e;
            }
        }
    }

    private void sleepWithJitter(int attempt){
        try{
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 5L + 5L * Math.min(attempt, 10)));
        } catch (InterruptedException ie){
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ie);
        }
    }
}
