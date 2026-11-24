package com.shop.backend.payment.infrastructure;

import com.shop.backend.payment.domain.Payment;
import com.shop.backend.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);
    boolean existsByOrder_IdAndStatus(Long orderId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKeyForUpdate(@Param("paymentKey") String paymentKey);
}
