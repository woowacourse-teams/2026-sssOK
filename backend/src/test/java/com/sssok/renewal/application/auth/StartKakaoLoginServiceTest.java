package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.config.KakaoOAuthProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class StartKakaoLoginServiceTest {
    @Test
    void 설정값과_저장된_state로_URL을_만들고_확인값과_비밀키는_포함하지_않는다() {
        OAuthLoginAttemptService attempts = mock(OAuthLoginAttemptService.class);
        String redirectUri = "https://example.com/auth/callback/kakao?next=/rooms&label=한글+값";
        KakaoOAuthProperties properties = new KakaoOAuthProperties("client+id", "secret-value", redirectUri, "admin-key");
        Instant expiresAt = Instant.parse("2026-10-25T12:05:00Z");
        when(attempts.start(SocialProvider.KAKAO, List.of("anonymous-token"))).thenReturn(
            new OAuthLoginAttemptResult(SocialProvider.KAKAO, "server-state", "private-verifier", null, redirectUri, expiresAt));

        SocialLoginStartResult result = new StartKakaoLoginService(attempts, properties).start(List.of("anonymous-token"));
        URI url = URI.create(result.authorizationUrl());
        Map<String, String> parameters = Arrays.stream(url.getRawQuery().split("&"))
            .map(part -> part.split("=", 2))
            .collect(Collectors.toMap(part -> part[0], part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8)));

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(url.getPath()).isEqualTo("/oauth/authorize");
        assertThat(parameters).containsOnlyKeys("response_type", "client_id", "redirect_uri", "state")
            .containsEntry("response_type", "code")
            .containsEntry("client_id", "client+id")
            .containsEntry("redirect_uri", redirectUri)
            .containsEntry("state", "server-state");
        assertThat(result.loginVerifier()).isEqualTo("private-verifier");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.authorizationUrl()).doesNotContain("private-verifier", "secret-value", "anonymous-token");
        assertThat(result.toString()).doesNotContain("private-verifier", "server-state");
    }
}
