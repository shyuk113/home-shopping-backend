package com.shop.backend.member.application.dto.response;

import com.shop.backend.member.domain.Member;

public record MemberResponseDto(
    String name,
    String phone,
    String address
){
    public static MemberResponseDto from(Member member){
        return new MemberResponseDto(member.getName(), member.getPhone(), member.getAddress());
    }
}
