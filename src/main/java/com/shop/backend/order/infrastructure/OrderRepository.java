package com.shop.backend.order.infrastructure;

import com.shop.backend.order.domain.Order;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByMemberId(Long memberId);

    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    @Query("""
        select o from Order o
        join fetch o.orderItems oi
        join fetch oi.item
        where o.member.id = :memberId
        """)
    List<Order> findWithItemsByMemberId(@Param("memberId") Long memberId);

    Page<Order> findByMemberId(Long memberId, Pageable pageable);
}
