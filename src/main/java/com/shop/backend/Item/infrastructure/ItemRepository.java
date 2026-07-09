package com.shop.backend.Item.infrastructure;


import com.shop.backend.Item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

    // 비관적 락 대신 DB의 원자적 UPDATE로 재고를 차감한다.
    // WHERE 절의 quantity >= :quantity 조건이 재고 부족 체크를 대체하며,
    // 반환된 영향받은 row 수(0 또는 1)로 성공 여부를 판단한다. 락을 오래 들고 있지 않아
    // 같은 상품에 대한 동시 요청이 몰려도 대기 없이 짧게 직렬화된다.
    @Modifying
    @Query("UPDATE Item i SET i.quantity = i.quantity - :quantity, "
            + "i.status = CASE WHEN (i.quantity - :quantity) = 0 THEN com.shop.backend.Item.domain.ItemStatus.SOLD_OUT ELSE i.status END "
            + "WHERE i.id = :id AND i.quantity >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
