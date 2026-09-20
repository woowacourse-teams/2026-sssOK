package com.sssok.application.port.out;

import java.time.Instant;

// 회원용 TokenProvider 와 따로 두는 이유는 두 토큰이 서로의 API 로 새어 들어가면 안 되기 때문이다.
// 발급·검증 경로를 나눠 두면 관리자 토큰을 회원으로 오인하는 일이 구조적으로 막힌다.
public interface AdminTokenProvider {

    IssuedAdminToken issue(Long adminId, Instant now);

    // 만료·서명 검증에 실패하거나 관리자 토큰이 아니면 UnauthorizedException 을 던진다.
    Long parseAdminId(String token);

    record IssuedAdminToken(String value, Instant expiresAt) {
    }
}
