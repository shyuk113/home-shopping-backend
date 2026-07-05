package com.shop.backend.member.application.dto.request;

public record MemberUpdateRequestDto(
    String name,
    String phone,
    String address
) {

}
