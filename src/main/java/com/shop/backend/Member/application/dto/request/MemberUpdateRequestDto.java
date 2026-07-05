package com.shop.backend.Member.application.dto.request;

public record MemberUpdateRequestDto(
    String name,
    String phone,
    String address
) {

}
