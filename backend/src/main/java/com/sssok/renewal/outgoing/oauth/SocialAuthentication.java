package com.sssok.renewal.outgoing.oauth;

import com.sssok.renewal.domain.member.SocialIdentity;
import java.util.Objects;

// 애플 연결 해제에는 제공자 토큰을 사용한다. 외부 API 응답으로 직렬화하거나 평문으로 저장하지 않는다.
public record SocialAuthentication(SocialIdentity identity, String accessToken, String refreshToken) {
    public SocialAuthentication { Objects.requireNonNull(identity); }

    @Override
    public String toString() { return "SocialAuthentication[REDACTED]"; }
}
