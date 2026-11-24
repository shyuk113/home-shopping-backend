package com.shop.backend.payment.domain;

import java.util.Optional;

/**
 * Payment 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 PaymentRepositoryAdapter 가 담당한다.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findByPaymentKeyForUpdate(String paymentKey);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
