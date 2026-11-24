package com.shop.backend.seller.domain;

import java.util.Optional;

/**
 * Seller 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 SellerRepositoryAdapter 가 담당한다.
 */
public interface SellerRepository {

    Seller save(Seller seller);

    Optional<Seller> findById(Long id);
}
