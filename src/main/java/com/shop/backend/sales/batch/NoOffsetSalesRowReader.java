package com.shop.backend.sales.batch;

import com.shop.backend.order.domain.OrderStatus;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;


import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
/**
 * No-Offset(키셋) 페이징 Reader.
 *
 * OFFSET 대신 "마지막으로 읽은 정렬 키보다 큰 행"을 조건으로 다음 페이지를 가져온다.
 *   OFFSET 방식 : ... ORDER BY k LIMIT 1000 OFFSET 500000   → 앞의 50만 행을 매번 만들고 버린다
 *   No-Offset   : ... WHERE k > (마지막 키) ORDER BY k LIMIT 1000 → 이미 읽은 행은 처음부터 걸러진다
 *
 * 정렬 키는 (판매일, 판매자, 주문ID)로 커서 Reader와 같다. 마지막으로 "반환한" 키를 ExecutionContext에 저장해
 * 재시작 시 그 다음 행부터 이어서 읽는다. (미리 가져왔지만 반환하지 않은 버퍼는 재시작 시 다시 조회된다)
 */
public class NoOffsetSalesRowReader implements ItemStreamReader<SalesRow> {

    private static final String CONTEXT_KEY = "noOffsetSalesRowReader.lastKey";

    private static final String PAGE_SQL = """
            SELECT o.id AS order_id,
            i.seller_id AS seller_id,
            CAST(o.order_time AS date) AS sales_date,
            o.status AS status,
            SUM(oi.quantity) AS quantity,
            SUM(oi.order_price * oi.quantity) AS amount
            FROM orders o
            JOIN order_item oi ON oi.order_id = o.id
            JOIN item i ON i.id = oi.item_id
            WHERE o.order_time >= ? AND o.order_time < ?
            AND o.status IN ('PAID', 'CANCEL')
            AND (CAST(o.order_time AS date),
            i.seller_id,o.id) > (?,?,?)
            GROUP BY o.id, i.seller_id, o.order_time, o.status
            ORDER BY CAST(o.order_time AS date),
            i.seller_id, o.id
            LIMIT ?
          """;

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate from;
    private final LocalDate to;
    private final int pageSize;

    private final Deque<SalesRow> buffer = new ArrayDeque<>();
    private SortKey lastFetchedKey;
    private SortKey lastReturnedKey;
    private boolean exhausted;

    public NoOffsetSalesRowReader(JdbcTemplate jdbcTemplate, LocalDate from, LocalDate to, int pageSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.from = from;
        this.to = to;
        this.pageSize = pageSize;
    }

    @Override
    public void open(ExecutionContext executionContext){
        SortKey initial = new SortKey(from.minusDays(1), 0L, 0L);
        lastReturnedKey = executionContext.containsKey(CONTEXT_KEY)
                ? SortKey.parse(executionContext.getString(CONTEXT_KEY))
                : initial;
        lastFetchedKey = lastReturnedKey;
        buffer.clear();
        exhausted = false;
    }

    @Override
    public SalesRow read(){
        if(buffer.isEmpty() && !exhausted){
            fetchNextPage();
        }
        SalesRow row = buffer.poll();
        if(row != null){
            lastReturnedKey = SortKey.of(row);
        }
        return row;
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putString(CONTEXT_KEY, lastReturnedKey.format());
    }

    private void fetchNextPage() {
        List<SalesRow> page = jdbcTemplate.query(PAGE_SQL,
                (rs, rowNum) -> new SalesRow(
                        rs.getLong("order_id"),
                        rs.getLong("seller_id"),
                        rs.getObject("sales_date", LocalDate.class),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getInt("quantity"),
                        rs.getLong("amount")),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                lastFetchedKey.salesDate(), lastFetchedKey.sellerId(), lastFetchedKey.orderId(),
                pageSize);

        buffer.addAll(page);
        if (page.size() < pageSize) {
            exhausted = true;
        }
        if (!page.isEmpty()) {
            lastFetchedKey = SortKey.of(page.get(page.size() - 1));
        }
    }

    private record SortKey(LocalDate salesDate, long sellerId, long orderId) {

        static SortKey of(SalesRow row) {
            return new SortKey(row.salesDate(), row.sellerId(), row.orderId());
        }

        static SortKey parse(String value) {
            String[] parts = value.split("\\|");
            return new SortKey(LocalDate.parse(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        }

        String format() {
            return salesDate + "|" + sellerId + "|" + orderId;
        }
    }
}

