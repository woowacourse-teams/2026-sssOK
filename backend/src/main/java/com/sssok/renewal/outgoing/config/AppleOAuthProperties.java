package com.sssok.renewal.outgoing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("oauth.apple")
public record AppleOAuthProperties(String clientId, String teamId, String keyId, String privateKey, String redirectUri) {
    public void validate() {
        Assert.hasText(clientId, "oauth.apple.client-id가 필요합니다");
        Assert.hasText(teamId, "oauth.apple.team-id가 필요합니다");
        Assert.hasText(keyId, "oauth.apple.key-id가 필요합니다");
        Assert.hasText(privateKey, "oauth.apple.private-key가 필요합니다");
        OAuthClientConfig.requireHttpsRedirect(redirectUri);
    }

    @Override public String toString() { return "AppleOAuthProperties[REDACTED]"; }
}
