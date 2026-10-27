package com.shop.backend.global.exception;

public class PaymentFailedException extends RuntimeException{

    private final String reason;

    public PaymentFailedException(String reason) {
        super("결제에 실패했습니다: "+ reason);
        this.reason = reason;
    }

    public String getReason(){
        return reason;
    }
}
