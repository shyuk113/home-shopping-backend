package com.shop.backend.Cart.domain.repository;

import com.shop.backend.Cart.domain.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart,Long>{

}
