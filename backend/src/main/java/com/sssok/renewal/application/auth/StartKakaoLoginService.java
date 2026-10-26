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

    public SocialLoginStartResult start(String anonymousAccessToken) {
        return startMany(anonymousAccessToken == null ? List.of() : List.of(anonymousAccessToken));
    }

    public SocialLoginStartResult startMany(List<String> anonymousAccessTokens) {
        OAuthLoginAttemptResult attempt = oauthLoginAttemptService.startMany(SocialProvider.KAKAO, anonymousAccessTokens);
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
