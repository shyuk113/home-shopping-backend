package com.shop.backend.payment.infrastructure;

import com.shop.backend.payment.domain.Payment;
import com.shop.backend.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);
    boolean existsByOrder_IdAndStatus(Long orderId, PaymentStatus status);
}
