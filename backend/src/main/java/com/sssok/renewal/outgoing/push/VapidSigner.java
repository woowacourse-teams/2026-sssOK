package com.sssok.renewal.outgoing.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

// RFC 8292(VAPID). 구독할 때 브라우저에 준 공개키의 짝인 개인키로 서명해, 이 서버가 보낸 요청임을 증명한다.
public class VapidSigner {
    // 푸시 서비스는 24시간을 넘는 만료를 거절한다. 시계 오차를 감안해 짧게 둔다.
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = ENCODER.encodeToString(
        "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final ECPrivateKey privateKey;
    private final String publicKey;
    private final String subject;
    private final Clock clock;

    public VapidSigner(String publicKey, String privateKey, String subject, Clock clock) {
        this.privateKey = VapidKeys.privateKey(privateKey);
        this.publicKey = publicKey;
        this.subject = subject;
        this.clock = clock;
    }

    public String authorization(String endpoint) {
        return "vapid t=" + token(audience(endpoint)) + ", k=" + publicKey;
    }

    private String token(String audience) {
        long expiresAt = clock.instant().plus(TOKEN_LIFETIME).getEpochSecond();
        String claims = "{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}".formatted(audience, expiresAt, subject);
        String signingInput = HEADER + "." + ENCODER.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        try {
            // JWS 는 DER 이 아니라 R||S(64바이트) 형식의 서명을 쓴다.
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 서명에 실패했습니다", e);
        }
    }

    // aud 는 경로를 뺀 푸시 서비스 오리진이다(예: https://fcm.googleapis.com).
    private static String audience(String endpoint) {
        URI uri = URI.create(endpoint);
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port;
    }
}
