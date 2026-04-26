package com.shop.backend.global.exception;

public class DuplicateCouponException extends RuntimeException {
    public DuplicateCouponException(String message){
        super(message);
    }
}
