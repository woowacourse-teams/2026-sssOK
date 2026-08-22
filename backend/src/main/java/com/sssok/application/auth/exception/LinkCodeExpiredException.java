package com.sssok.application.auth.exception;

public class LinkCodeExpiredException extends RuntimeException {

    public LinkCodeExpiredException() {
        super("만료된 코드입니다");
    }
}
