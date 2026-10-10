package com.shop.backend.Item.application;

import com.shop.backend.Item.application.dto.request.ItemUpdateRequestDto;
import com.shop.backend.Item.application.dto.response.ItemResponseDto;
import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemCategory;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;

import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;

import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;



import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ItemServiceTest {

    @TestConfiguration
    static class TestCacheConfig{
        @Bean
        @Primary
        public CacheManager cacheManager(){
            return new ConcurrentMapCacheManager("item", "itemList");
        }
    }

    @Autowired
    private ItemService itemService;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SellerRepository sellerRepository;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache("item").clear();
        cacheManager.getCache("itemList").clear();
    }


    @Test
    void updateItemWithCachePutRaceCondition() throws InterruptedException {

        Seller seller = sellerRepository.save(Seller.createSeller("테스트"));
        Item item =Item.createItem(seller ,"신발1", 10000, 5, "설명", "url", ItemStatus.SELLING, ItemCategory.SHOES);

        Long itemId = itemRepository.save(item).getId();

        //구버전 스냅샷
        ItemResponseDto staleSnapshot = itemService.getItemDetail(itemId);

        //그 사이 다른 요청이 상품을 수정
        ItemUpdateRequestDto newDto = new ItemUpdateRequestDto("신발2",5000,10, "설명", "url", ItemStatus.SELLING, ItemCategory.SHOES);
        itemService.updateItemWithCachePut(itemId, newDto);

        ItemResponseDto afterUpdate = (ItemResponseDto) cacheManager.getCache("item").get(itemId).get();
        assertThat(afterUpdate.price()).isEqualTo(5000);

        cacheManager.getCache("item").put(itemId, staleSnapshot);

        ItemResponseDto broken =  (ItemResponseDto) cacheManager.getCache("item").get(itemId).get();
        assertThat(broken.price()).isEqualTo(10000);

    }

    @Test
    void delayedDoubleDeleteRemovesStaleValue() throws InterruptedException {
        Seller seller = sellerRepository.save(Seller.createSeller("테스트"));
        Item item =Item.createItem(seller,"신발1", 10000, 5, "설명", "url", ItemStatus.SELLING, ItemCategory.SHOES);
        Member member = Member.createAdmin("관리자", "010-5555-5555", "text@text.com", "123456", Member.Role.ADMIN, "주소");
        Long userId = memberRepository.save(member).getId();
        Long itemId = itemRepository.save(item).getId();

        // 1. 최신값 캐싱 -> 이후 stale 스냅샷으로 사용
        ItemResponseDto staleSnapshot = itemService.getItemDetail(itemId);

        // 2. 정상 업데이트: 1차 evict 즉시 발생, 커밋 후 2차 evict가 500ms 뒤로 예약 (500ms는 임의값이며, 실무라면 이 서비스의 실제 조회 p99 레이턴시를 측정해서 안전마진을 곱해 정해야함)
        ItemUpdateRequestDto newDto = new ItemUpdateRequestDto("신발2",5000,10, "설명", "url", ItemStatus.SELLING, ItemCategory.SHOES);
        itemService.updateItem(itemId, newDto, userId);

        // 3. 늦게 끝난 조회가 stale값을 다시 채워 넣는 상황 재현
        cacheManager.getCache("item").put(itemId, staleSnapshot);
        assertThat(((ItemResponseDto)cacheManager.getCache("item").get(itemId).get()).price()).isEqualTo(10000);

        // 4. 2차 지연 삭제가 실행될 때까지 대기
        Thread.sleep(700);

        // 5. stale 에트리가 지워져서 다음 조회는 DB에서 새로 읽어오게됨
        assertThat(cacheManager.getCache("item").get(itemId)).isNull();
    }
}
