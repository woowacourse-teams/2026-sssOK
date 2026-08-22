package com.sssok.application.auth.exception;

public class LinkCodeNotFoundException extends RuntimeException {

    public LinkCodeNotFoundException() {
        super("유효하지 않은 코드입니다");
    }
}
