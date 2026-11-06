package com.sssok.renewal.outgoing.config;

import com.sssok.renewal.domain.auth.WebOrigin;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

// redirectUris는 허용할 카카오 콜백의 전체 주소 목록이다. 각 주소의 출처(scheme://host[:port])가 로그인을 시작한 웹의 출처가 된다.
// appId는 카카오 개발자 콘솔의 숫자 앱 ID이며 REST API 키(clientId)와 다르다. 앱 SDK 로그인(access token 검증)에만 쓰고,
// 비어 있으면 웹 로그인은 그대로 동작하고 앱 SDK 로그인만 사용할 수 없다.
@ConfigurationProperties("oauth.kakao")
public record KakaoOAuthProperties(String clientId, String clientSecret, List<String> redirectUris, String adminKey,
                                   String appId) {
    private static final String APP_ID_PATTERN = "[1-9][0-9]{0,17}";

    public KakaoOAuthProperties {
        redirectUris = redirectUris == null ? List.of()
            : redirectUris.stream().filter(uri -> uri != null && !uri.isBlank()).map(String::trim).toList();
        appId = appId == null ? null : appId.trim();
    }

    public void validate() {
        Assert.hasText(clientId, "oauth.kakao.client-id가 필요합니다");
        Assert.hasText(clientSecret, "oauth.kakao.client-secret이 필요합니다");
        Assert.hasText(adminKey, "oauth.kakao.admin-key가 필요합니다");
        Assert.notEmpty(redirectUris, "oauth.kakao.redirect-uris에 콜백 주소가 하나 이상 필요합니다");
        Assert.isTrue(appId == null || appId.isEmpty() || appId.matches(APP_ID_PATTERN),
            "oauth.kakao.app-id는 카카오 개발자 콘솔의 숫자 앱 ID여야 합니다");
        callbacksByOrigin();
    }

    public boolean nativeLoginConfigured() {
        return appId != null && appId.matches(APP_ID_PATTERN);
    }

    // 요청 출처와 정확히 일치하는 등록 콜백만 돌려준다. 와일드카드나 포트 추정은 하지 않는다.
    public Optional<String> callbackFor(String origin) {
        return Optional.ofNullable(callbacksByOrigin().get(origin));
    }

    public boolean isAllowedCallback(String redirectUri) {
        return redirectUri != null && callbacksByOrigin().containsValue(redirectUri);
    }

    private Map<String, String> callbacksByOrigin() {
        Map<String, String> callbacks = new LinkedHashMap<>();
        for (String redirectUri : redirectUris) {
            String origin = requireValidCallback(redirectUri);
            if (callbacks.put(origin, redirectUri) != null) {
                throw new IllegalArgumentException("oauth.kakao.redirect-uris에 같은 출처의 콜백이 중복되었습니다: " + origin);
            }
        }
        return callbacks;
    }

    // HTTPS만 허용한다. 예외로 정확히 localhost 호스트의 HTTP 콜백만 허용한다.
    private static String requireValidCallback(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            String origin = WebOrigin.originOfUrl(redirectUri);
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (!https && !"localhost".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException();
            }
            return origin;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("카카오 콜백 주소는 사용자 정보와 fragment가 없는 경로 포함 HTTPS 주소여야 하며, "
                + "HTTP는 localhost만 허용합니다");
        }
    }

    @Override public String toString() { return "KakaoOAuthProperties[REDACTED]"; }
}
