package com.shop.backend.cart.infrastructure;

import com.shop.backend.cart.domain.Cart;
import com.shop.backend.cart.domain.CartRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CartRepositoryAdapter implements CartRepository {

    private final CartJpaRepository jpa;

    @Override
    public Cart save(Cart cart) {
        return jpa.save(cart);
    }

    @Override
    public Optional<Cart> findByMemberId(Long memberId) {
        return jpa.findByMemberId(memberId);
    }
}
