package com.shop.backend.payment.application;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.global.exception.PaymentFailedException;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.order.application.OrderService;
import com.shop.backend.payment.domain.PaymentMethod;
import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(properties = {
        // local 프로필을 쓰지 않는다: 1,000만 건 shop DB를 가리킬 여지 자체를 없앰
        "spring.datasource.url=jdbc:postgresql://localhost:5433/shop_k6",
        "spring.datasource.username=shop",
        "spring.datasource.password=shop",
        "spring.datasource.hikari.maximum-pool-size=50",
        "spring.jpa.hibernate.ddl-auto=create",          // shop_k6는 부하 테스트용 일회성 DB
        "spring.sql.init.mode=never",
        "spring.kafka.listener.auto-startup=false"
})
@EnabledIfSystemProperty(named = "stock.bench", matches = "true")
public class StockStrategyBenchTest {

    private static final int STOCK = 100;
    private static final int REQUESTS = 300;
    private static final int PRICE = 10_000;

    @Autowired
    private PaymentConfirmFacade paymentConfirmFacade;

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

    @Autowired
    private EntityManagerFactory emf;

    @Value("${stock.strategy:atomic}")
    private String strategy;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void measure() throws InterruptedException{
        Seller seller = sellerRepository.save(Seller.createSeller("벤치"));
        Long itemId = itemRepository.save(Item.builder()
                .seller(seller)
                .name("인기 상품")
                .price(PRICE)
                .quantity(STOCK)
                .status(ItemStatus.SELLING)
                .build()).getId();
        redisTemplate.delete("stock:gate:" + itemId);

        List<Long> memberIds = new ArrayList<>();
        List<String> paymentKeys = new ArrayList<>();
        for(int i = 0; i < REQUESTS; i++){
            Long memberId = memberRepository.save(Member.builder()
                    .name("bench").phone(String.format("013%08d", i)).email("bench" + i + "@test.com")
                    .password("password").role(Member.Role.USER).address("서울").build()).getId();
            Long orderId = orderService.order(memberId, itemId, 1);
            memberIds.add(memberId);
            paymentKeys.add(paymentService.ready(orderId, PaymentMethod.CARD, "bench-pg", memberId).paymentKey());
        }

        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUESTS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();
        ConcurrentLinkedDeque<Long> latencies = new ConcurrentLinkedDeque<>();
        ConcurrentLinkedQueue<Long> successLatencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> failLatencies = new ConcurrentLinkedQueue<>();

        for(int i = 0; i < REQUESTS; i++){
            Long memberId = memberIds.get(i);
            String paymentKey = paymentKeys.get(i);
            executor.submit(() -> {
                ready.countDown();
                try{
                    start.await();
                    long t = System.nanoTime();
                    try{
                        paymentConfirmFacade.confirm(paymentKey, PRICE, memberId);
                        success.incrementAndGet();
                        successLatencies.add((System.nanoTime() - t) / 1_000_000);
                    } catch (PaymentFailedException e) {
                        outOfStock.incrementAndGet();
                        failLatencies.add((System.nanoTime() - t) / 1_000_000);
                    } catch (Exception e) {
                        error.incrementAndGet();
                    } finally {
                        latencies.add((System.nanoTime() - t)/1_000_000);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long begin = System.nanoTime();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        start.countDown();
        done.await();

        long totalMs = ((System.nanoTime() - begin)/1_000_000);
        executor.shutdown();

        int finalStock = itemRepository.findById(itemId).orElseThrow().getQuantity();
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        System.out.printf("[STOCK] strategy=%s success=%d outOfStock=%d error=%d finalStock=%d oversell=%d lostUpdate=%s total=%dms p50=%dms p95=%dms max=%dms%n",
                strategy, success.get(), outOfStock.get(), error.get(), finalStock,
                Math.max(0, success.get() - STOCK),
                (STOCK - finalStock) != success.get(),          // 성공 건수만큼 재고가 줄지 않았으면 Lost Update
                totalMs,
                sorted.get(sorted.size() / 2), sorted.get((int) (sorted.size() * 0.95)), sorted.get(sorted.size() - 1));


        System.out.printf("[STOCK-SPLIT] strategy=%s success{p50=%d p95=%d max=%d} fail{p50=%d p95=%d max=%d}%n",
                strategy,
                pct(successLatencies, 0.5), pct(successLatencies, 0.95), pct(successLatencies, 1.0),
                pct(failLatencies, 0.5), pct(failLatencies, 0.95), pct(failLatencies, 1.0));
        System.out.printf("[STOCK-RETRY] strategy=%s optimisticFailures=%d%n", strategy, stats.getOptimisticFailureCount());
        System.out.printf("[STOCK-GATE] strategy=%s redis=%s db=%d%n",
                strategy, redisTemplate.opsForValue().get("stock:gate:" + itemId), finalStock);
    }

    private static long pct(ConcurrentLinkedQueue<Long> values, double p) {
        if (values.isEmpty()) return -1;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * p)));
    }
}
