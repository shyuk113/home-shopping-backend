package com.shop.backend.payment.application;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.order.domain.Order;
import com.shop.backend.order.domain.OrderItem;
import com.shop.backend.order.infrastructure.OrderRepository;
import com.shop.backend.payment.domain.PaymentMethod;
import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/shop_k6",
        "spring.datasource.username=shop",
        "spring.datasource.password=shop",
        "spring.datasource.hikari.maximum-pool-size=50",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.sql.init.mode=never",
        "spring.kafka.listener.auto-startup=false"
})
@EnabledIfSystemProperty(named = "stock.bench", matches = "true")
public class MultiItemDeadlockBenchTest {

    private static final int ORDERS = 100;
    private static final int STOCK = 1_000;
    private static final int PRICE = 1_000;

    @Autowired
    private PaymentConfirmFacade paymentConfirmFacade;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Value("${stock.strategy:atomic}")
    private String strategy;

    @Test
    void measure() throws InterruptedException{
        Seller seller = sellerRepository.save(Seller.createSeller("데드락"));
        Item a = itemRepository.save(Item.builder().seller(seller).name("A").price(PRICE)
                .quantity(STOCK).status(ItemStatus.SELLING).build());
        Item b = itemRepository.save(Item.builder().seller(seller).name("B").price(PRICE)
                .quantity(STOCK).status(ItemStatus.SELLING).build());

        List<Long> memberIds = new ArrayList<>();
        List<String> paymentKeys = new ArrayList<>();

        for(int i = 0; i < ORDERS; i++) {
            Member member = memberRepository.save(Member.builder()
                    .name("dl").phone(String.format("015%08d", i)).email("dl" + i + "@test.com")
                    .password("password").role(Member.Role.USER).address("서울").build());
            List<OrderItem> items = (i % 2 == 0)
                    ? List.of(OrderItem.createOrderItem(a, PRICE, 1), OrderItem.createOrderItem(b, PRICE, 1))
                    : List.of(OrderItem.createOrderItem(b, PRICE, 1), OrderItem.createOrderItem(a, PRICE, 1));
            Long orderId = orderRepository.save(Order.createOrder(member, items)).getId();
            memberIds.add(member.getId());
            paymentKeys.add(paymentService.ready(orderId, PaymentMethod.CARD, "test-pg", member.getId()).paymentKey());

        }
        ExecutorService executor = Executors.newFixedThreadPool(ORDERS);
        CountDownLatch ready = new CountDownLatch(ORDERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ORDERS);
        AtomicInteger success = new AtomicInteger();
        Map<String, Integer> errors = new ConcurrentHashMap<>();

        for(int i =0; i <ORDERS; i++){

            Long memberId = memberIds.get(i);
            String paymentKey = paymentKeys.get(i);
            executor.submit(() -> {
                ready.countDown();
                try{
                    start.await();
                    paymentConfirmFacade.confirm(paymentKey, PRICE*2, memberId);
                    success.incrementAndGet();
                } catch (Exception e){
                    errors.merge(e.getClass().getSimpleName(),1,Integer::sum);
                } finally{
                    done.countDown();
                }
            });
        }

        ready.await();
        long begin = System.nanoTime();
        start.countDown();
        done.await();
        long totalMs = (System.nanoTime() - begin)/1_000_000;
        executor.shutdown();

        int stockA = itemRepository.findById(a.getId()).orElseThrow().getQuantity();
        int stockB = itemRepository.findById(b.getId()).orElseThrow().getQuantity();
        System.out.printf("[DEADLOCK] strategy=%s success=%d errors=%s stockA=%d stockB=%d totalMs=%dms%n",
                strategy, success.get(), errors, stockA, stockB, totalMs);
    }
}
