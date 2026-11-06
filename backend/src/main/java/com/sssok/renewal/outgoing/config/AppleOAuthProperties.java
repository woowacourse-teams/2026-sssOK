package com.sssok.renewal.outgoing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

// nativeClientId는 앱 SDK 로그인용 client 식별자(앱 번들 ID)이다. 비어 있으면 웹 로그인은 그대로 동작하고 앱 SDK 로그인만 사용할 수 없다.
// 앱용 client_secret도 같은 팀 키(teamId, keyId, privateKey)로 서명하므로 별도 키 설정은 두지 않는다.
@ConfigurationProperties("oauth.apple")
public record AppleOAuthProperties(String clientId, String teamId, String keyId, String privateKey, String redirectUri,
                                   String nativeClientId) {
    public AppleOAuthProperties {
        nativeClientId = nativeClientId == null ? null : nativeClientId.trim();
    }

    public void validate() {
        Assert.hasText(clientId, "oauth.apple.client-id가 필요합니다");
        Assert.hasText(teamId, "oauth.apple.team-id가 필요합니다");
        Assert.hasText(keyId, "oauth.apple.key-id가 필요합니다");
        Assert.hasText(privateKey, "oauth.apple.private-key가 필요합니다");
        OAuthClientConfig.requireHttpsRedirect(redirectUri);
        if (nativeLoginConfigured()) {
            Assert.isTrue(nativeClientId.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)),
                "oauth.apple.native-client-id에 공백이나 제어 문자를 넣을 수 없습니다");
            Assert.isTrue(!nativeClientId.equals(clientId),
                "oauth.apple.native-client-id는 웹 client-id와 달라야 합니다");
        }
    }

    public boolean nativeLoginConfigured() {
        return nativeClientId != null && !nativeClientId.isEmpty();
    }

    @Override public String toString() { return "AppleOAuthProperties[REDACTED]"; }
}
