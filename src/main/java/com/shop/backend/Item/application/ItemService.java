package com.shop.backend.Item.application;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.Item.application.dto.response.ItemDetailDto;
import com.shop.backend.Item.application.dto.request.ItemSaveDto;
import com.shop.backend.Item.application.dto.request.ItemUpdateDto;
import com.shop.backend.Item.application.dto.response.ItemResponseDto;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public ItemResponseDto getItemDetail(Long id){

        ItemDetailDto detail = findItemDetail(id);
        int stock = getStock(id);

        return new ItemResponseDto(
            detail.id(),
            detail.name(),
            detail.description(),
            detail.price(),
            stock,
            detail.status()
        );
    }

    @Cacheable(value = "item", key = "#p0")
    @Transactional(readOnly = true)
    public ItemDetailDto findItemDetail(Long id){
        log.debug("DB 조회 실행됨 - id: {}", id);

        Item item = itemRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));

        return new ItemDetailDto(
            item.getId(),
            item.getName(),
            item.getDescription(),
            item.getPrice(),
            //item.getQuantity(),
            item.getStatus()
        );
    }

    @Cacheable(value = "itemList")
    @Transactional(readOnly = true)
    public List<ItemDetailDto> findAllItemDetail(){
        return itemRepository.findAll().stream()
            .map(item -> new ItemDetailDto(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getStatus()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public int getStock(Long id){
        Item item = itemRepository.findById(id).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 상품입니다"));
        return item.getQuantity();
    }



    @Transactional
    public Long saveItem(ItemSaveDto itemSaveDto){
        return itemRepository.save(itemSaveDto.toEntity()).getId();
    }

    @CacheEvict(value = "item", key = "#p0") //상품 수정시 캐시 무효화
    @Transactional
    public void updateItem(Long itemId, ItemUpdateDto itemUpdateDto){
        Item item = itemRepository.findById(itemId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 상품입니다."));
        item.update(itemUpdateDto.itemName(),itemUpdateDto.price(),itemUpdateDto.quantity());
    }

    @CacheEvict(value = "item", key = "#p0") //상품 삭제 시 캐시 제거
    @Transactional
    public void deleteItem(Long id){
        itemRepository.deleteById(id);
    }

    @Caching(evict = {
        @CacheEvict(value = "item", key = "#p0"),
        @CacheEvict(value = "itemList", allEntries = true) //재고 변경 시 전체 상품 목록 캐시 무효화
    })
    @Transactional
    public void reduceStock(Long itemId, int quantity){
        Item item = itemRepository.findById(itemId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 상품입니다."));
        item.removeStock(quantity);
    }

}
