package com.shop.backend.cart.domain;

import java.util.Optional;

/**
 * Cart 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 CartRepositoryAdapter 가 담당한다.
 */
public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findByMemberId(Long memberId);
}
