package com.sssok.renewal.outgoing.push;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 서비스 계정 키로 서명한 JWT 를 Google OAuth 토큰과 바꾼다(RFC 7523). 토큰은 1시간 유효해 만료 전까지 재사용한다.
@Slf4j
public class FcmAccessTokenProvider {
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration ASSERTION_LIFETIME = Duration.ofHours(1);
    // 만료 직전에 쓴 토큰이 FCM 에 닿기 전에 끝나지 않게 여유를 둔다.
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
    // 발급이 실패하면 받는 사람마다 토큰 엔드포인트를 다시 두드리지 않게 잠시 쉰다.
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(30);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = ENCODER.encodeToString(
        "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final FcmServiceAccount account;
    private final RestClient restClient;
    private final Clock clock;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;
    private Instant retryAfter = Instant.EPOCH;

    public FcmAccessTokenProvider(FcmServiceAccount account, RestClient restClient, Clock clock) {
        this.account = account;
        this.restClient = restClient;
        this.clock = clock;
    }

    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(expiresAt.minus(REFRESH_MARGIN))) {
            return accessToken;
        }
        if (now.isBefore(retryAfter)) {
            throw new FcmAuthenticationException("FCM 액세스 토큰 발급이 직전에 실패해 잠시 건너뜁니다");
        }
        try {
            refresh(now);
            return accessToken;
        } catch (RuntimeException e) {
            accessToken = null;
            retryAfter = now.plus(FAILURE_BACKOFF);
            // 응답 본문에 키 내용은 없지만, 원인 타입·상태만 남겨 로그를 짧게 둔다.
            String status = e instanceof RestClientResponseException response
                ? String.valueOf(response.getStatusCode().value()) : "-";
            log.warn("FCM 액세스 토큰 발급 실패. status={}, error={}", status, e.getClass().getSimpleName());
            throw new FcmAuthenticationException("FCM 액세스 토큰을 받지 못했습니다");
        }
    }

    // FCM 이 401 을 돌려주면 다음 발송 때 새 토큰을 받는다.
    public synchronized void invalidate() {
        accessToken = null;
    }

    private void refresh(Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.add("assertion", assertion(now));
        JsonNode response = restClient.post()
            .uri(account.tokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !response.hasNonNull("access_token")) {
            throw new IllegalStateException("FCM 액세스 토큰 응답에 access_token 이 없습니다");
        }
        accessToken = response.get("access_token").asText();
        expiresAt = now.plusSeconds(response.path("expires_in").asLong(ASSERTION_LIFETIME.toSeconds()));
    }

    private String assertion(Instant now) {
        String claims = "{\"iss\":\"%s\",\"scope\":\"%s\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d}".formatted(
            account.clientEmail(), SCOPE, account.tokenUri(), now.getEpochSecond(),
            now.plus(ASSERTION_LIFETIME).getEpochSecond());
        String signingInput = HEADER + "." + ENCODER.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(account.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("FCM 서비스 계정 서명에 실패했습니다", e);
        }
    }
}
