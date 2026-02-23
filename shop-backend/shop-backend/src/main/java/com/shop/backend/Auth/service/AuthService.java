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

    public String signup(String email, String password,String name, String address, String phoneNumber){
        if (memberRepository.findByEmail(email).isPresent()){
            throw new RuntimeException("이미 가입된 이메일 입니다");
        }

        Member member = Member.builder()
            .email(email)
            .password(passwordEncoder.encode(password))
            .name("default")
            .address("default")
            .phone("default")
            .role(Member.Role.USER)
            .build();

        memberRepository.save(member);

        return "회원가입이 완료되었습니다"+ member.getEmail();
    }
}
