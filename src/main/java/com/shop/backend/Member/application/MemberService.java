package com.shop.backend.Member.application;

import com.shop.backend.Member.domain.Member;
import com.shop.backend.Member.infrastructure.MemberRepository;
import com.shop.backend.Member.application.dto.request.MemberUpdateRequestDto;
import com.shop.backend.Member.application.dto.response.MemberResponseDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberResponseDto getMember(Long memberId){
        Member member =memberRepository.findById(memberId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 회원입니다."));
        return MemberResponseDto.from(member);
    }

    @Transactional
    public MemberResponseDto updateMember(Long memberId, MemberUpdateRequestDto dto){
        Member member = memberRepository.findById(memberId).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 회원입니다."));
        member.update(dto.name(), dto.phone(), dto.address());
        return MemberResponseDto.from(member);
    }

}
