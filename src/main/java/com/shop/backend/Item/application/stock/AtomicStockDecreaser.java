package com.shop.backend.Item.application.stock;

import com.shop.backend.Item.infrastructure.ItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "stock.strategy", havingValue = "atomic", matchIfMissing = true)
public class AtomicStockDecreaser implements StockDecreaser {

    private final ItemRepository itemRepository;

    @Override
    public boolean decrease(Long itemId, int quantity){
        if(itemRepository.decreaseStock(itemId,quantity) == 1){
            return true;
        }
        if(!itemRepository.existsById(itemId)){
            throw new EntityNotFoundException("존재하지 않는 상품입니다.");
        }
        return false;
    }
}
