package com.shop.backend.Item.application.stock;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemRepository;
import com.shop.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.strategy", havingValue = "optimistic")
public class OptimisticStockDecreaser implements StockDecreaser {

    private final ItemRepository itemRepository;

    @Override
    public boolean decrease(Long itemId, int quantity){
        Item item = itemRepository.findById(itemId).orElseThrow(()-> new NotFoundException("존재하지 않는 상품입니다."));
        if(item.getQuantity() < quantity){
            return false;
        }
        item.removeStock(quantity);
        // 커밋 시: UPDATE item SET quantity=?, version=v+1 WHERE id=? AND version=v
        return true;
    }
}
