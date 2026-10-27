package com.sssok.renewal.domain.auth;

// DB에서 읽은 암호문이다. 제공자 토큰 원문과 구분한다.
public record EncryptedSocialTokens(String accessToken, String refreshToken) {
    @Override
    public String toString() { return "EncryptedSocialTokens[REDACTED]"; }
}
