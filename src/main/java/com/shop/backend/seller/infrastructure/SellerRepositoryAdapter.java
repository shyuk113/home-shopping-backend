package com.shop.backend.seller.infrastructure;

import com.shop.backend.seller.domain.Seller;
import com.shop.backend.seller.domain.SellerRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SellerRepositoryAdapter implements SellerRepository {

    private final SellerJpaRepository jpa;

    @Override
    public Seller save(Seller seller) {
        return jpa.save(seller);
    }

    @Override
    public Optional<Seller> findById(Long id) {
        return jpa.findById(id);
    }
}
