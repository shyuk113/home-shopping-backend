package com.shop.backend.seller.infrastructure;

import com.shop.backend.seller.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {
}
