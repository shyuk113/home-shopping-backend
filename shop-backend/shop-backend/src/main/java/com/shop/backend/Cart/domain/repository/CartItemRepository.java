package com.shop.backend.Cart.domain.repository;

import com.shop.backend.Cart.domain.model.Cart;
import com.shop.backend.Cart.domain.model.CartItem;
import com.shop.backend.Item.domain.model.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndItem(Cart cart, Item item);

    List<CartItem> findByCart_Member_Id(Long memberId);

    Optional<CartItem> findByItem_IdAndCart_Member_Id(Long itemId, Long MemberId);
}
