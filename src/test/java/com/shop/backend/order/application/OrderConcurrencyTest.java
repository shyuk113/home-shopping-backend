package com.shop.backend.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.payment.application.PaymentService;
import com.shop.backend.payment.application.dto.PaymentResponse;
import com.shop.backend.payment.domain.PaymentMethod;
import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OrderConcurrencyTest {

    private static final int STOCK = 10;
    private static final int THREAD_COUNT = 30;
    private static final int PRICE = 10_000;

    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SellerRepository sellerRepository;

    private Long itemId;

    @BeforeEach
    void setUp() {
        Seller seller = sellerRepository.save(Seller.createSeller("테스트"));

        Item item = Item.builder()
                .seller(seller)
                .name("한정판 상품")
                .price(PRICE)
                .quantity(STOCK)
                .status(ItemStatus.SELLING)
                .build();
        itemId = itemRepository.save(item).getId();
    }

    // 재고는 주문 생성이 아니라 결제 승인(confirm) 시점에 원자적 UPDATE로 차감된다.
    @Test
    @DisplayName("재고 10개인 상품의 결제 30건이 동시에 승인되면 정확히 10건만 성공하고 재고는 0이 된다")
    void concurrentConfirm_onlyStockCountSucceeds() throws InterruptedException {
        // 주문 생성과 결제 준비는 동시성 대상이 아니므로 미리 순차적으로 만들어 둔다
        List<Long> memberIds = new ArrayList<>();
        List<String> paymentKeys = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            // 같은 컨텍스트(DB)를 공유하는 CouponServiceTest와 전화번호(unique)가 겹치지 않도록 대역 분리
            Long memberId = createMember("011" + String.format("%08d", i)).getId();
            Long orderId = orderService.order(memberId, itemId, 1);
            PaymentResponse payment = paymentService.ready(orderId, PaymentMethod.CARD, "test-pg", memberId);
            memberIds.add(memberId);
            paymentKeys.add(payment.paymentKey());
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Long memberId = memberIds.get(i);
            String paymentKey = paymentKeys.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 모든 스레드가 동시에 출발하도록 대기
                    paymentService.confirm(paymentKey, PRICE, memberId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();     // 모든 스레드가 준비될 때까지 대기
        startLatch.countDown(); // 동시 실행 시작
        doneLatch.await();      // 전부 끝날 때까지 대기
        executor.shutdown();

        Item result = itemRepository.findById(itemId).orElseThrow();

        assertEquals(STOCK, successCount.get());
        assertEquals(THREAD_COUNT - STOCK, failCount.get());
        assertEquals(0, result.getQuantity());
        assertEquals(ItemStatus.SOLD_OUT, result.getStatus());
    }

    private Member createMember(String phone) {
        Member member = Member.builder()
            .name("tester")
            .phone(phone)
            .email(phone + "@test.com")
            .password("password")
            .role(Member.Role.USER)
            .address("서울")
            .build();
        return memberRepository.save(member);
    }
}
