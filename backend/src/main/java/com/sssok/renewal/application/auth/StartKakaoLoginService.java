package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.config.KakaoOAuthProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class StartKakaoLoginService {
    private static final String AUTHORIZATION_ENDPOINT = "https://kauth.kakao.com/oauth/authorize";

    private final OAuthLoginAttemptService oauthLoginAttemptService;
    private final KakaoOAuthProperties kakaoOAuthProperties;

    // requestOrigin은 컨트롤러가 Origin·Referer에서 정규화한 값이다. 등록된 콜백은 로그인 시도 생성 단계에서 고른다.
    public SocialLoginStartResult start(List<String> anonymousAccessTokens, String requestOrigin) {
        OAuthLoginAttemptResult attempt = oauthLoginAttemptService.start(SocialProvider.KAKAO, anonymousAccessTokens, requestOrigin);
        String authorizationUrl = UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
            .queryParam("response_type", "code")
            .queryParam("client_id", "{clientId}")
            .queryParam("redirect_uri", "{redirectUri}")
            .queryParam("state", "{state}")
            .encode()
            .buildAndExpand(kakaoOAuthProperties.clientId(), attempt.redirectUri(), attempt.state())
            .toUriString();
        return new SocialLoginStartResult(authorizationUrl, attempt.state(), attempt.loginVerifier(), attempt.expiresAt());
    }
}
