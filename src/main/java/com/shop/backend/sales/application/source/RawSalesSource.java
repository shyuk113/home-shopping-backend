package com.shop.backend.sales.application.source;

import com.shop.backend.sales.application.dto.DailySalesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dashboard.source", havingValue = "raw")
public class RawSalesSource implements SalesSource {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SQL = """
            SELECT CAST(o.order_time AS date) AS sales_date,
                   COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'PAID')                       AS order_count,
                   COALESCE(SUM(oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)              AS item_quantity,
                   COALESCE(SUM(oi.order_price * oi.quantity) FILTER (WHERE o.status = 'PAID'), 0)   AS sales_amount,
                   COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'CANCEL')                     AS cancel_count,
                   COALESCE(SUM(oi.order_price * oi.quantity) FILTER (WHERE o.status = 'CANCEL'), 0) AS cancel_amount
            FROM item i
            JOIN order_item oi ON oi.item_id = i.id
            JOIN orders o      ON o.id = oi.order_id
            WHERE i.seller_id = :sellerId
              AND o.order_time >= :from AND o.order_time < :toExclusive
              AND o.status IN ('PAID', 'CANCEL')
            GROUP BY CAST(o.order_time AS date)
            ORDER BY sales_date
            """;

    @Override
    public List<DailySalesResponse> findDaily(Long sellerId, LocalDate from, LocalDate to) {
        Map<String, Object> params = Map.of("sellerId", sellerId,
                "from", from.atStartOfDay(),
                "toExclusive", to.plusDays(1).atStartOfDay());

        return jdbc.query(SQL, params, (rs,n)-> new DailySalesResponse(
                rs.getObject("sales_date", LocalDate.class),
                rs.getInt("order_count"), rs.getInt("item_quantity"),
                        rs.getLong("sales_amount"),
                        rs.getInt("cancel_count"),
                        rs.getLong("cancel_amount"))
        );
    }
}
