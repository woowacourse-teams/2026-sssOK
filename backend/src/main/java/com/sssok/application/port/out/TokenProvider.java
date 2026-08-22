package com.sssok.application.port.out;

import java.time.Instant;

// 인증 토큰 발급 출력
public interface TokenProvider {

    IssuedToken issue(Long memberId, Instant now);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
