package com.shop.backend.payment.infrastructure;

import com.shop.backend.payment.domain.Payment;
import com.shop.backend.payment.domain.PaymentRepository;
import com.shop.backend.payment.domain.PaymentStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    @Override
    public Payment save(Payment payment) {
        return jpa.save(payment);
    }

    @Override
    public Optional<Payment> findByPaymentKey(String paymentKey) {
        return jpa.findByPaymentKey(paymentKey);
    }

    @Override
    public Optional<Payment> findByPaymentKeyForUpdate(String paymentKey) {
        return jpa.findByPaymentKeyForUpdate(paymentKey);
    }

    @Override
    public boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status) {
        return jpa.existsByOrder_IdAndStatus(orderId, status);
    }
}
