package com.sssok.renewal.outgoing.config;

import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthClient;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;


class OAuthClientConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(OAuthClientConfig.class);

    @Test
    void 기본_설정은_외부_인증_연동을_활성화하지_않는다() {
        runner.run(context -> assertThat(context).doesNotHaveBean(SocialAuthClient.class));
    }

    @Test
    void 활성화했는데_필수_설정이_없으면_시작하지_않는다() {
        runner.withPropertyValues("oauth.kakao.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 카카오_설정이_준비되면_클라이언트를_등록한다() {
        runner.withPropertyValues("oauth.kakao.enabled=true", "oauth.kakao.client-id=client", "oauth.kakao.client-secret=secret",
            "oauth.kakao.redirect-uris=https://example.com/callback", "oauth.kakao.admin-key=admin-secret",
            "oauth.token-encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]))
            .run(context -> assertThat(context).hasSingleBean(KakaoAuthClient.class));
    }

    @Test
    void 환경별_여러_콜백을_쉼표로_등록할_수_있다() {
        runner.withPropertyValues("oauth.kakao.enabled=true", "oauth.kakao.client-id=client", "oauth.kakao.client-secret=secret",
            "oauth.kakao.redirect-uris=https://dev.example.com/cb, http://localhost:3000/cb", "oauth.kakao.admin-key=admin-secret",
            "oauth.token-encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]))
            .run(context -> assertThat(context).hasSingleBean(KakaoAuthClient.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "http://example.com/cb", "http://localhost.evil.com/cb", "http://127.0.0.1:3000/cb", "https://user@example.com/cb",
        "https://example.com/cb#frag", "https://example.com", "ftp://example.com/cb", "not a uri", "https://a.example.com/cb,https://a.example.com/other"
    })
    void 카카오_활성화_시_잘못된_콜백_설정은_시작하지_않는다(String redirectUris) {
        runner.withPropertyValues("oauth.kakao.enabled=true", "oauth.kakao.client-id=client", "oauth.kakao.client-secret=secret",
            "oauth.kakao.redirect-uris=" + redirectUris, "oauth.kakao.admin-key=admin-secret",
            "oauth.token-encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 제공자를_활성화할_때_암호화_키가_없으면_시작하지_않는다() {
        runner.withPropertyValues("oauth.kakao.enabled=true", "oauth.kakao.client-id=client", "oauth.kakao.client-secret=secret",
            "oauth.kakao.redirect-uris=https://example.com/callback", "oauth.kakao.admin-key=admin-secret")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 카카오_어드민_키가_없으면_시작하지_않는다() {
        runner.withPropertyValues("oauth.kakao.enabled=true", "oauth.kakao.client-id=client",
            "oauth.kakao.client-secret=secret", "oauth.kakao.redirect-uris=https://example.com/callback",
            "oauth.token-encryption-key=" + Base64.getEncoder().encodeToString(new byte[32]))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 설정_객체_출력에_비밀값을_노출하지_않는다() {
        assertThat(new KakaoOAuthProperties("client", "sensitive-value", List.of("https://example.com/cb"), "admin-secret").toString()).doesNotContain("sensitive-value", "admin-secret");
        assertThat(new AppleOAuthProperties("client", "team", "key", "private-key", "https://example.com").toString()).doesNotContain("private-key");
    }
}
