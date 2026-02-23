package com.shop.backend.Order.application.service;

import com.shop.backend.Item.domain.model.Item;
import com.shop.backend.Item.domain.repository.ItemRepository;
import com.shop.backend.Member.domain.model.Member;
import com.shop.backend.Member.domain.repository.MemberRepository;
import com.shop.backend.Order.domain.model.Order;
import com.shop.backend.Order.domain.model.OrderItem;
import com.shop.backend.Order.domain.repository.OrderRepository;
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
        Member member = memberRepository.findById(memberId).orElseThrow();
        Item item = itemRepository.findById(itemId).orElseThrow();

        OrderItem orderItem = OrderItem.createOrderItem(item, item.getPrice(), quantity);

        Order order = Order.createOrder(member,List.of(orderItem));

        orderRepository.save(order);

        return order.getId();
    }
}
