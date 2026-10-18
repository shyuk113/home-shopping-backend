package com.shop.backend.sales.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chunk 안의 증가분을 (판매자, 날짜)별로 먼저 합친 뒤 daily_sales_summary에 누적 upsert한다.
 *
 * - 먼저 합치는 이유: 같은 키가 한 배치에 여러 번 들어가면 upsert가 키 수만큼 반복되고,
 *   reWriteBatchedInserts로 multi-row INSERT가 되면 PostgreSQL이 아예 에러를 냄.
 *   multi-row INSERT로 재작성될 경우 "ON CONFLICT DO UPDATE command cannot affect row a second time" 에러가 난다.
 * - 누적(+=)이어도 재시작에 안전한 이유: Writer의 쓰기와 Reader의 진행 위치(ExecutionContext)가
 *   같은 Chunk 트랜잭션으로 커밋되므로, 커밋된 Chunk는 재시작 시 다시 읽히지 않는다.
 */
@RequiredArgsConstructor
public class DailySalesSummaryWriter implements ItemWriter<SalesDelta> {

    private static final String UPSERT_SQL = """
            INSERT INTO daily_sales_summary(sales_date, seller_id, order_count, item_quantity,
            sales_amount, cancel_count, cancel_amount, created_at, updated_at)
            VALUES(?,?,?,?,?,?,?,now(), now())
            ON CONFLICT (seller_id, sales_date) DO UPDATE SET
            order_count   = daily_sales_summary.order_count   + EXCLUDED.order_count,
                            item_quantity = daily_sales_summary.item_quantity + EXCLUDED.item_quantity,
                            sales_amount  = daily_sales_summary.sales_amount  + EXCLUDED.sales_amount,
                            cancel_count  = daily_sales_summary.cancel_count  + EXCLUDED.cancel_count,
                            cancel_amount = daily_sales_summary.cancel_amount + EXCLUDED.cancel_amount,
                            updated_at    = now()
                            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void write(Chunk<? extends SalesDelta> chunk){
        Map<String, SalesDelta> merged = new LinkedHashMap<>();
        for(SalesDelta delta : chunk){
            merged.merge(delta.sellerId() + ":" +delta.salesDate(), delta, SalesDelta::plus);
        }

        List<SalesDelta> rows = new ArrayList<>(merged.values());
        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, rows.size(), (ps,row)->
        {
            ps.setObject(1, row.salesDate());
            ps.setLong(2, row.sellerId());
            ps.setInt(3, row.orderCount());
            ps.setInt(4, row.itemQuantity());
            ps.setLong(5, row.salesAmount());
            ps.setInt(6, row.cancelCount());
            ps.setLong(7, row.cancelAmount());
        });
    }
}
