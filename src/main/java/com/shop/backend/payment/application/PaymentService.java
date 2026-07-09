package com.shop.backend.payment.application;

import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.global.exception.OutOfStockException;
import com.shop.backend.order.domain.Order;
import com.shop.backend.order.domain.OrderItem;
import com.shop.backend.order.infrastructure.OrderRepository;
import com.shop.backend.payment.domain.Payment;
import com.shop.backend.payment.domain.PaymentMethod;
import com.shop.backend.payment.domain.PaymentStatus;
import com.shop.backend.payment.infrastructure.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ItemRepository itemRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public Payment ready(Long orderId, PaymentMethod method, String pgProvider){
        Order order = orderRepository.findById(orderId).orElseThrow(()->new EntityNotFoundException("존재 하지 않는 주문 입니다."));

        String paymentKey = UUID.randomUUID().toString();
        Payment payment = Payment.createPayment(order, paymentKey, method, order.getTotalPrice(),pgProvider, PaymentStatus.READY);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void confirm(String paymentKey, int approvedAmount){
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제 입니다."));
        Order order = payment.getOrder();

        if (order.getTotalPrice() != approvedAmount){
            payment.fail("결제 금액 불일치");
            order.markFailed();
            throw new IllegalStateException("결제 금액이 주문 금액과 일치 하지 않습니다.");
        }

        try{
            for(OrderItem orderItem : order.getOrderItems()){
                Long itemId = orderItem.getItem().getId();
                int updated = itemRepository.decreaseStock(itemId, orderItem.getQuantity());
                if (updated == 0){
                    if (!itemRepository.existsById(itemId)){
                        throw new EntityNotFoundException("존재 하지 않는 상품입니다.");
                    }
                    throw new OutOfStockException("재고가 부족합니다.");
                }
            }
        } catch(OutOfStockException e){
            payment.fail("재고 부족");
            order.markFailed();
            // TODO: 이 시점엔 PG 승인이 이미 났으므로, PG 취소(환불) API 호출이 반드시 필요함
            throw e;
        }

        payment.approve(LocalDateTime.now());
        order.markPaid();
    }

    @Transactional
    public void cancel(String paymentKey){
        Payment payment = paymentRepository.findByPaymentKey(paymentKey).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 결제 입니다."));
        payment.cancel();
    }

    @Transactional(readOnly = true)
    public Payment getPayment(String paymentKey){
        return paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(()-> new EntityNotFoundException("존재 하지 않는 결제 입니다"));
    }
}
