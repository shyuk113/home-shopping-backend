package com.shop.backend.global.exception;

/**
 * 조회 대상이 없을 때 던지는 도메인 예외 (404).
 * jakarta.persistence.NotFoundException 대신 사용해 서비스 계층이 JPA 에 의존하지 않도록 한다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
