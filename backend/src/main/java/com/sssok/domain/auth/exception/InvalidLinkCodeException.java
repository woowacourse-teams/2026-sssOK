package com.sssok.domain.auth.exception;

public class InvalidLinkCodeException extends RuntimeException {

    public InvalidLinkCodeException(String value) {
        super("코드 형식이 올바르지 않습니다: " + value);
    }
}
