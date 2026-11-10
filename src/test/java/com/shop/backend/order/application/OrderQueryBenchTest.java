package com.shop.backend.order.application;

import com.shop.backend.order.application.dto.response.OrderResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never"
})
@ActiveProfiles({"local", "bench"}) // bench가 마지막이어야 local의 create/always를 덮어쓴다
@EnabledIfSystemProperty(named = "bench", matches = "true")
public class OrderQueryBenchTest {

    private static final int WARMUP = 3;
    private static final int RUNS = 7;

    @Autowired
    private OrderService orderService;

    @Autowired
    private EntityManagerFactory emf;

    @ParameterizedTest
    @ValueSource(ints = {0,250,506})
    void measurePaged(int page) {

        long memberId= 1000L;
        Pageable pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "orderTime"));

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        for(int i = 0; i < WARMUP; i++) {
            orderService.getOrders(memberId, pageable);
        }

        List<Long> times = new ArrayList<>();
        long queries = 0;
        int size = 0;
        for (int i = 1; i <= RUNS; i++) {
            stats.clear();
            long start = System.nanoTime();
            Page<OrderResponse> result = orderService.getOrders(memberId, pageable);
            times.add((System.nanoTime() - start)/1_000_000);

            queries = stats.getPrepareStatementCount();
            size = result.getNumberOfElements();
        }

        Collections.sort(times);
        System.out.printf("[Page] page=%d size=%d queries=%d median=%dms min=%dms max=%dms%n",
                page, size, queries, times.get(RUNS/2), times.get(0), times.get(RUNS-1));
    }

    /*@ParameterizedTest
    @ValueSource(longs = {1000L, 711L})
    void measureGetOrders(long memberId){
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        orderService.getOrders(memberId);

        List<Long> times = new ArrayList<>();
        long queries = 0;
        int orderCount = 0;

        for(int i = 1; i <= RUNS; i++) {
            stats.clear();

            long start = System.nanoTime();
            List<OrderResponse> orders = orderService.getOrders(memberId);
            long ms = (System.nanoTime() - start) / 1_000_000;

            queries = stats.getPrepareStatementCount();
            orderCount = orders.size();
            times.add(ms);

            System.out.printf("[N+1] member=%d run=%d orders=%d queries=%d times=%dms%n", memberId, i, orderCount, queries, ms);

        }
        Collections.sort(times);
        System.out.printf("[N+1] member=%d SUMMARY orders=%d queries=%d median=%dms min=%dms max=%dms%n",
                memberId, orderCount, queries,
                times.get(RUNS / 2), times.get(0), times.get(RUNS - 1));

    }*/
}
