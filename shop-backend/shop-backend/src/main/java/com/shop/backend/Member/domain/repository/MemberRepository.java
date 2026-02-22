package com.shop.backend.Member.domain.repository;

import com.shop.backend.Member.domain.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

}
