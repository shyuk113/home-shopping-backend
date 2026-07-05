package com.shop.backend.coupon.application.dto.reqeust;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CouponCreateRequestDto(
    @NotBlank(message = "쿠폰 이름을 입력해주세요.")
    String name,
    @Min(value = 1, message = "할인 금액은 1 이상이어야 합니다.")
    int discountAmount,
    @Min(value = 1, message = "총 발급 수량은 1 이상이어야 합니다.")
    int totalQuantity,
    @NotNull(message = "발급 시작 날짜를 입력해주세요.")
    LocalDateTime startTime,
    @NotNull(message = "발급 종료 날짜를 입력해주세요.")
    LocalDateTime endTime) {

}
