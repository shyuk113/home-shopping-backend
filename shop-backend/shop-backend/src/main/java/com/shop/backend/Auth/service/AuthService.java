package com.shop.backend.Auth.service;

import com.shop.backend.Member.domain.model.Member;
import com.shop.backend.Member.domain.repository.MemberRepository;
import com.shop.backend.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public String login(String email, String password){

        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("이 이메일로 가입된 회원이 없습니다"));

        if (!passwordEncoder.matches(password, member.getPassword())){
            throw new RuntimeException("비밀번호가 틀립니다");
        }

        return jwtProvider.createAccessToken(
            member.getEmail(),
            member.getRole().name()
        );
    }
}
