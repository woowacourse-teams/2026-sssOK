package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.member.SocialProvider;
import java.time.Instant;

// 서버 내부 결과다. loginVerifier는 URL이나 제공자 요청에 싣지 않고 시작한 웹·앱에서 잠시 보관한다.
public record OAuthLoginAttemptResult(
    SocialProvider provider,
    String state,
    String loginVerifier,
    String nonce,
    String redirectUri,
    Instant expiresAt
) {
    @Override
    public String toString() {
        return "OAuthLoginAttemptResult[REDACTED]";
    }
}
