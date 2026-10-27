package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import org.springframework.util.LinkedMultiValueMap;

public class KakaoAuthClient implements SocialAuthClient {
    private static final String TOKEN_ENDPOINT = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_ENDPOINT = "https://kapi.kakao.com/v2/user/me";

    private final OAuthHttpClient oauthHttpClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String adminKey;

    public KakaoAuthClient(OAuthHttpClient oauthHttpClient, String clientId, String clientSecret, String redirectUri, String adminKey) {
        this.oauthHttpClient = oauthHttpClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.adminKey = adminKey;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialAuthentication authenticate(String authorizationCode, String expectedNonce) {
        validateAuthorizationCode(authorizationCode);

        JsonNode tokenResponse = exchangeAuthorizationCode(authorizationCode);
        String accessToken = OAuthHttpClient.readRequiredText(tokenResponse, "access_token");
        SocialIdentity identity = fetchUserIdentity(accessToken);

        return new SocialAuthentication(identity, accessToken, tokenResponse.path("refresh_token").asText(null));
    }

    @Override
    public void unlink(SocialAuthentication authentication) {
        if (authentication.identity().provider() != provider()) {
            throw SocialAuthException.invalid();
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", authentication.identity().providerUserId());
        JsonNode response = oauthHttpClient.postAdminUnlink("https://kapi.kakao.com/v1/user/unlink", adminKey, form);
        JsonNode id = response.path("id");
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0
            || !id.asText().equals(authentication.identity().providerUserId())) {
            throw SocialAuthException.unavailable();
        }
    }

    private void validateAuthorizationCode(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw SocialAuthException.invalid();
        }
    }

    private JsonNode exchangeAuthorizationCode(String authorizationCode) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        return oauthHttpClient.postForm(TOKEN_ENDPOINT, form);
    }

    private SocialIdentity fetchUserIdentity(String accessToken) {
        JsonNode userResponse = oauthHttpClient.getJson(USER_INFO_ENDPOINT, accessToken);
        JsonNode userId = userResponse.path("id");
        if (!userId.isIntegralNumber() || !userId.canConvertToLong() || userId.longValue() <= 0) {
            throw SocialAuthException.unavailable();
        }
        return new SocialIdentity(provider(), userId.asText());
    }
}
