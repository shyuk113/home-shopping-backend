package com.shop.backend.Item.application;

import com.shop.backend.Item.application.dto.request.ItemCreateRequestDto;
import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.Item.application.dto.response.ItemResponseDto;
import com.shop.backend.Item.application.dto.request.ItemUpdateRequestDto;
import com.shop.backend.global.exception.UnauthorizedException;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;

import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final ScheduledExecutorService cacheEvictScheduler;
    private final CacheManager cacheManager;
    private final SellerRepository sellerRepository;

    //상품 상세 조회(재고 표시)
    @Cacheable(value = "item", key = "#p0")
    @Transactional(readOnly = true)
    public ItemResponseDto getItemDetail(Long id){
        log.debug("DB 조회 실행됨 - id: {}", id);

        Item item = itemRepository.findById(id).orElseThrow(()->new EntityNotFoundException("존재하지 않는 상품입니다."));
        return ItemResponseDto.from(item);
    }

    //상품 목록 조회
    @Cacheable(value = "itemList")
    @Transactional(readOnly = true)
    public List<ItemResponseDto> getAllItemDetail(){
        return itemRepository.findAll().stream()
            .map(ItemResponseDto::from)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional(readOnly = true)
    public Page<ItemResponseDto> getItemByCategory(Pageable pageable, ItemCategory category){
        Page<Item> items = itemRepository.findByCategory(pageable, category);
        return items.map(ItemResponseDto::from);
    }

    //상품 등록
    @Transactional
    public ItemResponseDto createItem(Long userId, ItemCreateRequestDto request){
        Member member = memberRepository.findById(userId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 회원입니다."));
        if(member.getRole() != Member.Role.ADMIN){
            throw new UnauthorizedException("상품 등록은 관리자만 할 수 있습니다.");
        }
        Seller seller = sellerRepository.findById(request.sellerId()).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 판매자 입니다."));
        Item item = Item.createItem(seller, request.name(), request.price(), request.quantity(), request.description(), request.imageUrl(), request.status(), request.category());
        itemRepository.save(item);
        return  ItemResponseDto.from(item);
    }

    //상품 수정하기
    @CacheEvict(value = "item", key = "#p0") //상품 수정시 캐시 무효화
    @Transactional
    public void updateItem(Long itemId, ItemUpdateRequestDto request, Long userId) {
        Member member = memberRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));
        if (member.getRole() != Member.Role.ADMIN) {
            throw new UnauthorizedException("상품 등록은 관리자만 할 수 있습니다.");
        }
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));
        item.update(request.itemName(), request.price(), request.quantity(), request.description(), request.imageUrl(), request.status(), request.category());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    scheduleDelayedEvict(itemId);
                }
            });
        }
    }
    private   void scheduleDelayedEvict(Long itemId){
        cacheEvictScheduler.schedule(()->{
            Cache cache = cacheManager.getCache("item");
            if(cache != null){
                cache.evict(itemId);
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    //cachePut을 사용할 경우 (조회가 늦게 끝나서 최신값을 덮어쓰는 상황 인위적으로 재현)
    @CachePut(value = "item", key = "#p0")
    @Transactional
    public ItemResponseDto updateItemWithCachePut(Long itemId, ItemUpdateRequestDto request){
        Item item = itemRepository.findById(itemId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 상품입니다."));
        item.update(request.itemName(), request.price(), request.quantity(), request.description(), request.imageUrl(), request.status(), request.category());
        return ItemResponseDto.from(item);
    }

    //상품 삭제하기
    @CacheEvict(value = "item", key = "#p0") //상품 삭제 시 캐시 제거
    @Transactional
    public void deleteItem(Long id, Long userId){

        Member member = memberRepository.findById(userId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 회원입니다."));
        if(member.getRole() != Member.Role.ADMIN){
            throw new UnauthorizedException("상품 등록은 관리자만 할 수 있습니다.");
        }
        itemRepository.deleteById(id);
    }

    //상품 재고 차감하기
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
