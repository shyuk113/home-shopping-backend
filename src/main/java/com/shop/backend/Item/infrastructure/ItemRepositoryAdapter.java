package com.shop.backend.Item.infrastructure;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.Item.domain.ItemRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ItemRepositoryAdapter implements ItemRepository {

    private final ItemJpaRepository jpa;

    @Override
    public Item save(Item item) {
        return jpa.save(item);
    }

    @Override
    public Optional<Item> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Item> findAll() {
        return jpa.findAll();
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public Page<Item> findByCategory(Pageable pageable, ItemCategory category) {
        return jpa.findByCategory(pageable, category);
    }

    @Override
    public Optional<Item> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public int decreaseStock(Long id, int quantity) {
        return jpa.decreaseStock(id, quantity);
    }
}
