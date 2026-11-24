package com.shop.backend.order.infrastructure;

import com.shop.backend.order.domain.Order;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    Page<Order> findByMemberId(Long memberId, Pageable pageable);
}
