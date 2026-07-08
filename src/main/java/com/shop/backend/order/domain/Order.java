package com.shop.backend.order.domain;

import com.shop.backend.common.BaseEntity;
import com.shop.backend.member.domain.Member;
import com.shop.backend.common.Address;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.*;

@Entity
@Getter @Setter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Embedded
    private Address deliveryAddress;

    private LocalDateTime orderTime;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Builder
    private Order(Member member, List<OrderItem> orderItems, OrderStatus status) {
        this.member = member;
        this.orderItems = orderItems;
        this.status = status;
    }

    public static Order createOrder(Member member, List<OrderItem> orderItems){
        return Order.builder()
                .member(member)
                .orderItems(orderItems)
                .status(OrderStatus.PENDING)
                .build();
    }

    public void addOrderItem(OrderItem orderItem){
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    public void markPaid(){
        if (this.status != OrderStatus.PENDING){
            throw new IllegalStateException("결제 대기 상태의 주문만 결제 완료로 전환할 수 있습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void markFailed(){
        this.status = OrderStatus.FAILED;
    }

    public int getTotalPrice(){
        return orderItems.stream().mapToInt(OrderItem::getTotalPrice).sum();
    }
}
