package com.shop.backend.global.exception;

public class CouponOutOfStockException extends RuntimeException{
    public CouponOutOfStockException(String message){
        super(message);
    }
}
