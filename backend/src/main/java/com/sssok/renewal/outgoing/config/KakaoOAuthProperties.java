package com.sssok.renewal.outgoing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("oauth.kakao")
public record KakaoOAuthProperties(String clientId, String clientSecret, String redirectUri, String adminKey) {
    public void validate() {
        Assert.hasText(clientId, "oauth.kakao.client-id가 필요합니다");
        Assert.hasText(clientSecret, "oauth.kakao.client-secret이 필요합니다");
        Assert.hasText(adminKey, "oauth.kakao.admin-key가 필요합니다");
        OAuthClientConfig.requireHttpsRedirect(redirectUri);
    }

    @Override public String toString() { return "KakaoOAuthProperties[REDACTED]"; }
}
