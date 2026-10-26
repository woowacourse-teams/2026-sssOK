package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.outgoing.security.TokenProvider.IssuedToken;
import java.time.Instant;

public record AuthResult(String accessToken, Long userId, String nickname, Instant expiresAt,
                         String refreshToken, Instant refreshExpiresAt, String memberStatus) {
    public AuthResult(String accessToken, Long userId, String nickname, Instant expiresAt,
                      String refreshToken, Instant refreshExpiresAt) {
        this(accessToken, userId, nickname, expiresAt, refreshToken, refreshExpiresAt, "ANONYMOUS");
    }

    public static AuthResult from(Member member, IssuedToken token) {
        return new AuthResult(token.value(), member.getId(), member.getDisplayName().value(),
            token.expiresAt(), token.refreshToken(), token.refreshExpiresAt(), member.getStatus().name());
    }

    @Override
    public String toString() { return "AuthResult[REDACTED]"; }
}
