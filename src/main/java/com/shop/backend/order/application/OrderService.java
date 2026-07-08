package com.shop.backend.order.application;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.global.exception.OutOfStockException;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.order.domain.Order;
import com.shop.backend.order.domain.OrderItem;
import com.shop.backend.order.domain.OrderStatus;
import com.shop.backend.order.infrastructure.OrderRepository;
import com.shop.backend.order.application.dto.response.OrderResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public Long order(Long memberId, Long itemId, int quantity){
        Member member = memberRepository.findById(memberId).orElseThrow(()-> new EntityNotFoundException("Member not found"));
        Item item = itemRepository.findById(itemId).orElseThrow(()-> new EntityNotFoundException("Item not found"));

        if (item.getQuantity() <= quantity){
            throw new OutOfStockException("재고가 부족합니다. 현재 남은 재고: " + item.getQuantity());
        }

        OrderItem orderItem = OrderItem.createOrderItem(item, item.getPrice(), quantity);

        Order order = Order.createOrder(member,List.of(orderItem));

        orderRepository.save(order);

        return order.getId();
    }

    @Transactional(readOnly = true) //회원의 전체 주문 목록
    public List<OrderResponse> getOrders(Long memberId){
        return orderRepository.findByMemberId(memberId).stream()
            .map(OrderResponse::from)
            .toList();
    }

    @Transactional(readOnly = true) // 단건 조회
    public OrderResponse getOrder(Long orderId){
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        return OrderResponse.from(order);
    }

    @Transactional // 주문 취소
    public void cancelOrder(Long orderId){
        Order order = orderRepository.findById(orderId)
            .orElseThrow(()-> new EntityNotFoundException("Order not found"));

        if(order.getStatus() == OrderStatus.CANCEL){
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }

        boolean wasPaid = order.getStatus() == OrderStatus.PAID;
        order.setStatus(OrderStatus.CANCEL);

        if(wasPaid) {
            order.getOrderItems().forEach(OrderItem::cancel);
        }
    }
}
