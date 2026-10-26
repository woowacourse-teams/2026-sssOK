package com.sssok.renewal.outgoing.config;

import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
import com.sssok.renewal.outgoing.oauth.AppleClientSecretGenerator;
import com.sssok.renewal.outgoing.oauth.AppleSigningKeyProvider;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.OAuthHttpClient;
import java.net.URI;
import com.sssok.renewal.outgoing.security.SocialTokenCipher;
import org.springframework.beans.factory.annotation.Value;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({KakaoOAuthProperties.class, AppleOAuthProperties.class})
public class OAuthClientConfig {
    @Bean
    public SocialTokenCipher socialTokenCipher(@Value("${oauth.token-encryption-key:}") String key) {
        return new SocialTokenCipher(key);
    }

    @Bean
    @ConditionalOnProperty(name = "oauth.kakao.enabled", havingValue = "true")
    public KakaoAuthClient kakaoAuthClient(KakaoOAuthProperties properties, SocialTokenCipher tokenCipher) {
        properties.validate();
        tokenCipher.requireConfigured();
        return new KakaoAuthClient(createHttpClient(), properties.clientId(), properties.clientSecret(), properties.redirectUri(), properties.adminKey());
    }

    @Bean
    @ConditionalOnProperty(name = "oauth.apple.enabled", havingValue = "true")
    public AppleAuthClient appleAuthClient(AppleOAuthProperties properties, SocialTokenCipher tokenCipher) {
        properties.validate();
        tokenCipher.requireConfigured();
        OAuthHttpClient httpClient = createHttpClient();
        AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
            properties.clientId(), properties.teamId(), properties.keyId(), properties.privateKey()
        );
        return new AppleAuthClient(
            httpClient, new AppleSigningKeyProvider(httpClient), clientSecretGenerator,
            properties.clientId(), properties.redirectUri()
        );
    }

    private OAuthHttpClient createHttpClient() {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new OAuthHttpClient(RestClient.builder().requestFactory(factory).build());
    }

    static void requireHttpsRedirect(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("OAuth redirect-uri는 사용자 정보와 fragment가 없는 HTTPS 주소여야 합니다");
        }
    }
}
