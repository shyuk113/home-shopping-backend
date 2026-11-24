package com.shop.backend.order.infrastructure;

import com.shop.backend.order.domain.Order;
import com.shop.backend.order.domain.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpa;

    @Override
    public Order save(Order order) {
        return jpa.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Order> findByIdAndMemberId(Long id, Long memberId) {
        return jpa.findByIdAndMemberId(id, memberId);
    }

    @Override
    public Page<Order> findByMemberId(Long memberId, Pageable pageable) {
        return jpa.findByMemberId(memberId, pageable);
    }
}
