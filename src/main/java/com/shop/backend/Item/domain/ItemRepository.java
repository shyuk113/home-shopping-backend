package com.shop.backend.Item.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Item 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 ItemRepositoryAdapter 가 담당한다.
 */
public interface ItemRepository {

    Item save(Item item);

    Optional<Item> findById(Long id);

    List<Item> findAll();

    boolean existsById(Long id);

    void deleteById(Long id);

    Page<Item> findByCategory(Pageable pageable, ItemCategory category);

    Optional<Item> findByIdForUpdate(Long id);

    int decreaseStock(Long id, int quantity);
}
