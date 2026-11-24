package com.shop.backend.cart.domain;

import com.shop.backend.Item.domain.Item;
import java.util.List;
import java.util.Optional;

/**
 * CartItem 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 CartItemRepositoryAdapter 가 담당한다.
 */
public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    void delete(CartItem cartItem);

    Optional<CartItem> findByCartAndItem(Cart cart, Item item);

    List<CartItem> findByMemberId(Long memberId);

    Optional<CartItem> findByItemIdAndMemberId(Long itemId, Long memberId);
}
