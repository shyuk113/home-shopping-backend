package com.shop.backend.Member.application.dto.response;

import com.shop.backend.Member.domain.Member;

public record MemberResponseDto(
    String name,
    String phone,
    String address
){
    public static MemberResponseDto from(Member member){
        return new MemberResponseDto(member.getName(), member.getPhone(), member.getAddress());
    }
}
