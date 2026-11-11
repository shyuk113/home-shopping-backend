package com.shop.backend.Item.application.stock;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.infrastructure.ItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.strategy", havingValue = "naive")
public class NaiveStockDecreaser implements StockDecreaser {

    private final ItemRepository itemRepository;

    @Override
    public boolean decrease(Long itemId, int quantity){
        Item item = itemRepository.findById(itemId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 상품입니다."));

        if(item.getQuantity() < quantity){
            return false;
        }
        item.removeStock(quantity);
        return true;
    }
}
