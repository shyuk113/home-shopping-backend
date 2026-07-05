package com.shop.backend.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
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

    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long itemId;

    @BeforeEach
    void setUp() {
        Item item = Item.builder()
            .name("한정판 상품")
            .price(10_000)
            .quantity(STOCK)
            .status(ItemStatus.SELLING)
            .build();
        itemId = itemRepository.save(item).getId();
    }

    @Test
    @DisplayName("재고 10개인 상품에 30명이 동시에 주문하면 정확히 10건만 성공하고 재고는 0이 된다")
    void concurrentOrder_onlyStockCountSucceeds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Long memberId = createMember("010" + String.format("%08d", i)).getId();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 모든 스레드가 동시에 출발하도록 대기
                    orderService.order(memberId, itemId, 1);
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
