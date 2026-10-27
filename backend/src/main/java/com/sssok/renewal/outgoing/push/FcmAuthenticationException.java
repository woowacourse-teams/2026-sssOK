package com.sssok.renewal.outgoing.push;

// 액세스 토큰을 받지 못했다. 기기 토큰과 상관없는 실패라 그 토큰을 만료로 보면 안 된다.
public class FcmAuthenticationException extends RuntimeException {
    public FcmAuthenticationException(String message) {
        super(message);
    }
}
