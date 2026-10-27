package com.sssok.renewal.domain.auth;

import com.sssok.renewal.domain.member.SocialProvider;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

// 로그인 요청의 만료·생성 조건을 관리한다. state와 로그인 확인값은 해시로 보관한다.
public record OAuthLoginAttempt(
    String stateHash,
    String loginVerifierHash,
    SocialProvider provider,
    String redirectUri,
    String nonce,
    List<Long> anonymousMemberIds,
    Instant createdAt,
    Instant expiresAt
) {
    public OAuthLoginAttempt {
        anonymousMemberIds = List.copyOf(anonymousMemberIds);
        requireHash(stateHash);
        requireHash(loginVerifierHash);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(redirectUri);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("로그인 시도 만료 시각은 생성 시각 이후여야 합니다");
        }
        if (anonymousMemberIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("유효한 익명 회원 ID가 필요합니다");
        }
        if (provider == SocialProvider.APPLE && (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}"))) {
            throw new IllegalArgumentException("애플 로그인에는 서버에서 생성한 nonce가 필요합니다");
        }
        if (provider == SocialProvider.KAKAO && nonce != null) {
            throw new IllegalArgumentException("카카오 사용자 정보 조회 방식은 nonce를 사용하지 않습니다");
        }
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256 해시가 필요합니다");
        }
    }

    @Override
    public String toString() {
        return "OAuthLoginAttempt[REDACTED]";
    }
}
