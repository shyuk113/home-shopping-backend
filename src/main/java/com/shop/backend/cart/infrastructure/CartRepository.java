package com.shop.backend.cart.infrastructure;

import com.shop.backend.cart.domain.Cart;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart,Long>{

    Optional<Cart> findByMemberId(Long memberId);
}
