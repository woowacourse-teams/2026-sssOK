package com.sssok.renewal.outgoing.oauth;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

// Apple .p8 키는 설정으로만 주입한다. 요청마다 짧게 유효한 ES256 client_secret을 생성한다.
public class AppleClientSecretGenerator {
    private static final String TOKEN_AUDIENCE = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final PrivateKey privateKey;

    public AppleClientSecretGenerator(String clientId, String teamId, String keyId, String privateKeyPem) {
        this.clientId = clientId;
        this.teamId = teamId;
        this.keyId = keyId;
        this.privateKey = parseSigningKey(privateKeyPem);
    }

    public String generate() {
        Instant now = Instant.now();
        return Jwts.builder()
            .header().keyId(keyId).and()
            .issuer(teamId)
            .subject(clientId)
            .audience().add(TOKEN_AUDIENCE).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(CLIENT_SECRET_TTL)))
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact();
    }

    private PrivateKey parseSigningKey(String privateKeyPem) {
        try {
            String encodedKey = privateKeyPem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            PrivateKey signingKey = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            if (!(signingKey instanceof ECPrivateKey ecPrivateKey)
                || ecPrivateKey.getParams().getCurve().getField().getFieldSize() != 256) {
                throw new IllegalArgumentException();
            }
            return signingKey;
        } catch (Exception e) {
            throw new IllegalArgumentException("Apple 로그인 서명 키 설정이 올바르지 않습니다");
        }
    }
}
