package com.sssok.renewal.domain.member;

import java.util.Objects;

public record SocialIdentity(SocialProvider provider, String providerUserId) {
    public SocialIdentity {
        Objects.requireNonNull(provider, "소셜 제공자가 필요합니다");
        if (providerUserId == null || providerUserId.isBlank() || providerUserId.length() > 255) {
            throw new IllegalArgumentException("소셜 사용자 ID는 1~255자여야 합니다");
        }
    }
}
