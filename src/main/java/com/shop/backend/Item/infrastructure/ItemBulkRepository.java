package com.shop.backend.Item.infrastructure;

import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.Item.domain.ItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ItemBulkRepository {

    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public void bulkInsert(List<ItemRow> rows) {
        String sql = """
                INSERT INTO item (seller_id, name, price, quantity, description, image_url, status, category, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (ps, row) -> {
            ps.setLong(1, row.sellerId());
            ps.setString(2, row.name());
            ps.setInt(3, row.price());
            ps.setInt(4, row.quantity());
            ps.setString(5, row.description());
            ps.setString(6, row.status().name());
            ps.setString(7, row.category().name());
            ps.setObject(8, row.createdAt());
            ps.setObject(9, row.createdAt());
        });
    }

    /** 주문 시딩 시 참조할 상품 (id, price) 목록 */
    public List<IdPrice> findAllIdAndPrice() {
        return jdbcTemplate.query("SELECT id, price FROM item ORDER BY id",
                (rs, i) -> new IdPrice(rs.getLong("id"), rs.getInt("price")));
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM item", Long.class);
    }

    public record ItemRow(Long sellerId, String name, int price, int quantity, String description,
                          ItemStatus status, ItemCategory category, LocalDateTime createdAt) {
    }

    public record IdPrice(long id, int price) {
    }
}
