package com.sssok.application.port.out;

import java.time.Instant;

// 인증 토큰 발급 출력
public interface TokenProvider {

    IssuedToken issue(Long memberId, Instant now);

    // 토큰이 없거나 형식이 잘못됐거나 만료·서명 검증에 실패하면 UnauthorizedException을 던진다.
    Long parse(String token);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
