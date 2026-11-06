package com.sssok.renewal.domain.auth;

// DB에서 읽은 암호문이다. 제공자 토큰 원문과 구분한다.
// clientType은 이 토큰을 발급한 제공자 client 설정이다. 저장된 값이 없는 기존 토큰은 웹 설정으로 본다.
public record EncryptedSocialTokens(String accessToken, String refreshToken, OAuthClientType clientType) {
    public EncryptedSocialTokens {
        clientType = clientType == null ? OAuthClientType.WEB : clientType;
    }

    @Override
    public String toString() { return "EncryptedSocialTokens[REDACTED]"; }
}
