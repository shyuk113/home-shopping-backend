package com.shop.backend.cart.infrastructure;

import com.shop.backend.Item.domain.Item;
import com.shop.backend.cart.domain.Cart;
import com.shop.backend.cart.domain.CartItem;
import com.shop.backend.cart.domain.CartItemRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartItemRepositoryAdapter implements CartItemRepository {

    private final CartItemJpaRepository jpa;

    @Override
    public CartItem save(CartItem cartItem) {
        return jpa.save(cartItem);
    }

    @Override
    public void delete(CartItem cartItem) {
        jpa.delete(cartItem);
    }

    @Override
    public Optional<CartItem> findByCartAndItem(Cart cart, Item item) {
        return jpa.findByCartAndItem(cart, item);
    }

    @Override
    public List<CartItem> findByMemberId(Long memberId) {
        return jpa.findByCart_Member_Id(memberId);
    }

    @Override
    public Optional<CartItem> findByItemIdAndMemberId(Long itemId, Long memberId) {
        return jpa.findByItem_IdAndCart_Member_Id(itemId, memberId);
    }
}
