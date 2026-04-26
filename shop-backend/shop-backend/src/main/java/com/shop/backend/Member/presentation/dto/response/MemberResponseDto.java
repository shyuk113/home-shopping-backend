package com.shop.backend.Member.presentation.dto.response;

import com.shop.backend.Member.domain.model.Member;

public record MemberResponseDto(
    String name,
    String phone,
    String address
){
    public static MemberResponseDto from(Member member){
        return new MemberResponseDto(member.getName(), member.getPhone(), member.getAddress());
    }
}
