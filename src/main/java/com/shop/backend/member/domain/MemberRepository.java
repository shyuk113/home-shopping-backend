package com.shop.backend.member.domain;

import java.util.Optional;

/**
 * Member 영속성 포트(도메인 계층).
 * 서비스는 이 인터페이스에만 의존하고, 구현은 infrastructure 의 MemberRepositoryAdapter 가 담당한다.
 */
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findByEmail(String email);
}
