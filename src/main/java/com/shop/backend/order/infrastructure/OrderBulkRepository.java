package com.shop.backend.order.infrastructure;

import com.shop.backend.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대량 적재 전용 JDBC 레포지토리.
 * IDENTITY 전략에서는 Hibernate batch insert가 꺼지므로 JdbcTemplate.batchUpdate로 직접 적재한다.
 * 영속성 컨텍스트/더티 체킹/Auditing을 모두 우회하므로 일반 비즈니스 로직에서는 사용하지 않는다.
 */

@Repository
@RequiredArgsConstructor
public class OrderBulkRepository {

    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    /**
     * orders.id 시퀀스에서 ID를 미리 할당받는다.
     * batch insert는 생성된 키를 돌려받기 어려우므로, order_item이 참조할 order_id를 먼저 확보한다.
     */
    public List<Long> reserveIds(int count) {
        return jdbcTemplate.queryForList(
                "SELECT nextval(pg_get_serial_sequence('orders', 'id')) FROM generate_series(1, ?)",
                Long.class, count);
    }

    public void bulkInsertOrders(List<OrderRow> rows) {
        String sql = """
                INSERT INTO orders (id, member_id, city, street, zipcode, order_time, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.id());
            ps.setLong(2, row.memberId());
            ps.setString(3, row.city());
            ps.setString(4, row.street());
            ps.setString(5, row.zipcode());
            ps.setObject(6, row.orderTime());
            ps.setString(7, row.status().name());
            ps.setObject(8, row.orderTime());
            ps.setObject(9, row.orderTime());
        });
    }

    public void bulkInsertOrderItems(List<OrderItemRow> rows) {
        String sql = """
                INSERT INTO order_item (order_id, item_id, order_price, quantity)
                VALUES (?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.orderId());
            ps.setLong(2, row.itemId());
            ps.setInt(3, row.orderPrice());
            ps.setInt(4, row.quantity());
        });
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
    }

    public record OrderRow(Long id, Long memberId, String city, String street, String zipcode,
                           LocalDateTime orderTime, OrderStatus status) {
    }

    public record OrderItemRow(Long orderId, Long itemId, int orderPrice, int quantity) {
    }
}
