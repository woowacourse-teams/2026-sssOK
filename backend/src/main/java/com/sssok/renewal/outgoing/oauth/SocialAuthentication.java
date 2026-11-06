package com.sssok.renewal.outgoing.oauth;

import com.sssok.renewal.domain.auth.OAuthClientType;
import com.sssok.renewal.domain.member.SocialIdentity;
import java.util.Objects;

// 애플 연결 해제에는 제공자 토큰을 사용한다. 외부 API 응답으로 직렬화하거나 평문으로 저장하지 않는다.
// clientType은 토큰을 발급한 제공자 client 설정이다. 지정하지 않으면 웹 설정이다.
public record SocialAuthentication(SocialIdentity identity, String accessToken, String refreshToken,
                                   OAuthClientType clientType) {
    public SocialAuthentication {
        Objects.requireNonNull(identity);
        clientType = clientType == null ? OAuthClientType.WEB : clientType;
    }

    public SocialAuthentication(SocialIdentity identity, String accessToken, String refreshToken) {
        this(identity, accessToken, refreshToken, OAuthClientType.WEB);
    }

    @Override
    public String toString() { return "SocialAuthentication[REDACTED]"; }
}
