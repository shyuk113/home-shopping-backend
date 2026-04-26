package com.shop.backend.Order.application.service;

import com.shop.backend.Item.application.service.ItemService;
import com.shop.backend.Item.domain.model.Item;
import com.shop.backend.Item.domain.repository.ItemRepository;
import com.shop.backend.Member.domain.model.Member;
import com.shop.backend.Member.domain.repository.MemberRepository;
import com.shop.backend.Order.domain.model.Order;
import com.shop.backend.Order.domain.model.OrderItem;
import com.shop.backend.Order.domain.model.OrderStatus;
import com.shop.backend.Order.domain.repository.OrderRepository;
import com.shop.backend.Order.presentation.dto.response.OrderResponse;
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
    private final ItemService itemService;

    @Transactional
    public Long order(Long memberId, Long itemId, int quantity){
        Member member = memberRepository.findById(memberId).orElseThrow(()-> new EntityNotFoundException("Member not found"));
        Item item = itemRepository.findByIdWithLock(itemId).orElseThrow(()-> new EntityNotFoundException("Item not found"));

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

        order.setStatus(OrderStatus.CANCEL);
        order.getOrderItems().forEach(OrderItem::cancel);
    }
}
