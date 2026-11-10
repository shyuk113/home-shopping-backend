package com.shop.backend.order.application;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.Item.domain.ItemStatus;
import com.shop.backend.Item.infrastructure.ItemRepository;
import com.shop.backend.member.domain.Member;
import com.shop.backend.member.infrastructure.MemberRepository;
import com.shop.backend.order.application.dto.response.OrderResponse;
import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.infrastructure.SellerRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OrderQueryCountTest {

    private static final int ORDER_COUNT = 20;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EntityManagerFactory emf;

    @Test
    @DisplayName("주문 목록 조회 쿼리 수는 주문 건수에 비례하지 않는다.")
    void getOrders_queryCountIsConstant(){
        Member member = memberRepository.save(Member.builder()
                .name("tester").phone("01200000000").email("nplus1@test.com")
                .password("password").role(Member.Role.USER).address("서울")
                .build());

        Seller seller = sellerRepository.save(Seller.createSeller("N+1 테스트"));
        for (int i = 0; i < ORDER_COUNT; i++) {
            Item item = itemRepository.save(Item.builder()
                    .seller(seller).name("상품" + i).price(1000)
                    .quantity(10).status(ItemStatus.SELLING).build());
            orderService.order(member.getId(), item.getId(),1);
        }

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        //when
        Page<OrderResponse> orders = orderService.getOrders(member.getId(), PageRequest.of(0, 20));

        //then
        long queryCount = stats.getPrepareStatementCount();
        System.out.println("[N+1] orders= " + orders.getNumberOfElements()+ ", queries= "+ queryCount);

        assertThat(orders.getNumberOfElements()).isEqualTo(ORDER_COUNT);
        assertThat(queryCount).isLessThanOrEqualTo(4);
    }


}
