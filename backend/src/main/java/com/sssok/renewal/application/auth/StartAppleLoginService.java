package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.config.AppleOAuthProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class StartAppleLoginService {
    private static final String AUTHORIZATION_ENDPOINT = "https://appleid.apple.com/auth/authorize";

    private final OAuthLoginAttemptService oauthLoginAttemptService;
    private final AppleOAuthProperties appleOAuthProperties;

    public SocialLoginStartResult start(List<String> anonymousAccessTokens) {
        OAuthLoginAttemptResult attempt = oauthLoginAttemptService.start(SocialProvider.APPLE, anonymousAccessTokens);
        // 이름·이메일 scope 없이 code/query 방식을 사용한다. nonce는 서버에 저장한 값만 넣는다.
        String authorizationUrl = UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
            .queryParam("response_type", "code")
            .queryParam("response_mode", "query")
            .queryParam("client_id", "{clientId}")
            .queryParam("redirect_uri", "{redirectUri}")
            .queryParam("state", "{state}")
            .queryParam("nonce", "{nonce}")
            .encode()
            .buildAndExpand(appleOAuthProperties.clientId(), attempt.redirectUri(), attempt.state(), attempt.nonce())
            .toUriString();
        return new SocialLoginStartResult(authorizationUrl, attempt.state(), attempt.loginVerifier(), attempt.expiresAt());
    }
}
