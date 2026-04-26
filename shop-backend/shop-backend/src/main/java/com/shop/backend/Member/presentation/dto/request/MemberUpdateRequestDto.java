package com.shop.backend.Member.presentation.dto.request;

public record MemberUpdateRequestDto(
    String name,
    String phone,
    String address
) {

}
