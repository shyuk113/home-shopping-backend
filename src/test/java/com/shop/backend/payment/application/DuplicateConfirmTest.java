package com.shop.backend.payment.application;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.order.application.OrderService;
import com.shop.backend.payment.domain.PaymentMethod;
import com.shop.backend.payment.domain.PaymentStatus;
import com.shop.backend.payment.infrastructure.PaymentRepository;
import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DuplicateConfirmTest {

    private static final int STOCK = 10;
    private static final int DUPLICATES = 10;
    private static final int PRICE = 10_000;

    @Autowired
    private PaymentConfirmFacade paymentConfirmFacade;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("같은 결제를 동시에 여러번 해도 재고는 한번만 차감")
    void duplicateConfirm() throws InterruptedException{
        Seller seller = sellerRepository.save(Seller.createSeller("중복 확정"));
        Long itemId= itemRepository.save(Item.builder()
                .seller(seller).name("상품").price(PRICE)
                .quantity(STOCK).status(ItemStatus.SELLING).build()).getId();
        Long memberId = memberRepository.save(Member.builder()
                .name("tester").phone("01400000000").email("dup@test.com")
                .password("password").role(Member.Role.USER).address("서울").build()).getId();
        Long orderId = orderService.order(memberId, itemId, 1);
        String paymentKey = paymentService.ready(orderId, PaymentMethod.CARD, "test-pg", memberId).paymentKey();

        ExecutorService executor = Executors.newFixedThreadPool(DUPLICATES);
        CountDownLatch  ready = new CountDownLatch(DUPLICATES);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done   = new CountDownLatch(DUPLICATES);
        AtomicInteger error = new AtomicInteger();

        for(int i = 0; i < DUPLICATES; i++){
            executor.submit(() -> {
                ready.countDown();
                try{
                    start.await();
                    paymentConfirmFacade.confirm(paymentKey, PRICE, memberId);
                } catch(Exception e){
                    error.incrementAndGet();
                } finally{
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        int finalStock = itemRepository.findById(itemId).orElseThrow().getQuantity();
        System.out.printf("[DUP] finalStock=%d (excepted %d) error=%d%n", finalStock, STOCK-1, error.get());
        assertThat(finalStock).isEqualTo(STOCK-1);
        assertThat(paymentRepository.findByPaymentKey(paymentKey).orElseThrow().getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(error.get()).isZero();
    }
}
