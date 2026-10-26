package com.sssok.renewal.domain.member;

import java.time.Instant;
import java.util.Objects;

public record SocialAccount(Long id, Long memberId, SocialIdentity identity, Instant createdAt) {
    public SocialAccount {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("저장된 회원의 ID가 필요합니다");
        }
        Objects.requireNonNull(identity);
        Objects.requireNonNull(createdAt);
    }

    public static SocialAccount link(Long memberId, SocialIdentity identity, Instant now) {
        return new SocialAccount(null, memberId, identity, now);
    }
}
