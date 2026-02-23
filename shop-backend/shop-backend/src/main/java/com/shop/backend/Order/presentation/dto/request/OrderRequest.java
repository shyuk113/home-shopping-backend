package com.shop.backend.Order.presentation.dto.request;

public record OrderRequest(Long memberId, Long itemId, int quantity, String address) {

}
