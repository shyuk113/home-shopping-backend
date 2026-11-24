package com.shop.backend.order.domain;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Order 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 OrderRepositoryAdapter 가 담당한다.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    Page<Order> findByMemberId(Long memberId, Pageable pageable);
}
