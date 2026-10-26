package com.sssok.renewal.outgoing.security;

import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import java.time.Instant;

// 인증 토큰 발급 출력
public interface TokenProvider {

    default IssuedToken issue(Long memberId, Instant now) {
        return issue(memberId, now, RefreshTokenDelivery.COOKIE);
    }

    IssuedToken issue(Long memberId, Instant now, RefreshTokenDelivery delivery);

    // 토큰이 없거나 형식이 잘못됐거나 만료·서명 검증에 실패하면 UnauthorizedException을 던진다.
    Long parse(String token);

    record IssuedToken(String value, Instant expiresAt, String refreshToken, Instant refreshExpiresAt) {
        public IssuedToken(String value, Instant expiresAt) {
            this(value, expiresAt, null, null);
        }

        @Override
        public String toString() { return "IssuedToken[REDACTED]"; }
    }
}
