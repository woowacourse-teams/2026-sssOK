package com.sssok.renewal.outgoing.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import org.springframework.util.LinkedMultiValueMap;

public class KakaoAuthClient implements SocialAuthClient {
    private static final String TOKEN_ENDPOINT = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_ENDPOINT = "https://kapi.kakao.com/v2/user/me";
    private static final String ACCESS_TOKEN_INFO_ENDPOINT = "https://kapi.kakao.com/v1/user/access_token_info";

    private final OAuthHttpClient oauthHttpClient;
    private final String clientId;
    private final String clientSecret;
    private final String adminKey;
    private final String appId;

    public KakaoAuthClient(OAuthHttpClient oauthHttpClient, String clientId, String clientSecret, String adminKey,
                           String appId) {
        this.oauthHttpClient = oauthHttpClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.adminKey = adminKey;
        this.appId = appId;
    }

    // appId는 앱 SDK 로그인용 숫자 앱 ID이며 null이면 앱 SDK 로그인만 사용할 수 없다.
    // 앱 SDK가 받은 access token을 카카오에 조회해 사용자 번호를 확인한다. 요청에서 사용자 번호나 앱 ID를 받지 않는다.
    // 유효한 access token의 재사용을 막지는 않는다(로그인 시도·nonce 검증과 다르다).
    public SocialAuthentication authenticateAccessToken(String accessToken) {
        if (appId == null || appId.isBlank()) {
            throw SocialAuthException.unavailable();
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw SocialAuthException.invalid();
        }
        JsonNode info = oauthHttpClient.getTokenInfo(ACCESS_TOKEN_INFO_ENDPOINT, accessToken);
        long userId = requirePositiveLong(info.path("id"));
        long tokenAppId = requirePositiveLong(info.path("app_id"));
        long expiresIn = requireLong(info.path("expires_in"));
        if (!String.valueOf(tokenAppId).equals(appId)) {
            throw SocialAuthException.invalid();
        }
        if (expiresIn <= 0) {
            throw SocialAuthException.invalid();
        }
        return new SocialAuthentication(new SocialIdentity(provider(), String.valueOf(userId)), accessToken, null);
    }

    // 누락·타입 오류·범위 초과는 기본값으로 보정하지 않고 예상하지 못한 응답으로 본다.
    private static long requirePositiveLong(JsonNode value) {
        long number = requireLong(value);
        if (number <= 0) {
            throw SocialAuthException.unavailable();
        }
        return number;
    }

    private static long requireLong(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw SocialAuthException.unavailable();
        }
        return value.longValue();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialAuthentication authenticate(String authorizationCode, String expectedNonce, String redirectUri) {
        validateAuthorizationCode(authorizationCode);
        validateRedirectUri(redirectUri);

        JsonNode tokenResponse = exchangeAuthorizationCode(authorizationCode, redirectUri);
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

    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw SocialAuthException.invalid();
        }
    }

    private JsonNode exchangeAuthorizationCode(String authorizationCode, String redirectUri) {
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
