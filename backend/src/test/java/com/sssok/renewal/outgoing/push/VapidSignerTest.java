package com.sssok.renewal.outgoing.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VapidSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    private KeyPair keys;
    private String publicKey;
    private VapidSigner signer;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(VapidKeys.p256Spec());
        keys = generator.generateKeyPair();
        publicKey = base64Url(VapidKeys.uncompressed((ECPublicKey) keys.getPublic()));
        signer = new VapidSigner(publicKey, base64Url(scalar((ECPrivateKey) keys.getPrivate())),
            "mailto:team@sssok.test", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 공개키를_k로_싣고_JWT를_t로_싣는다() {
        String authorization = signer.authorization("https://fcm.googleapis.com/fcm/send/abc");

        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + publicKey);
    }

    @Test
    void 푸시_서비스_오리진을_aud로_12시간_만료로_서명한다() throws Exception {
        String token = token(signer.authorization("https://fcm.googleapis.com/fcm/send/abc"));
        String[] parts = token.split("\\.");

        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
            .contains("\"exp\":" + NOW.plusSeconds(12 * 3600).getEpochSecond())
            .contains("\"sub\":\"mailto:team@sssok.test\"");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(keys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    private static String token(String authorization) {
        return authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
    }

    private static byte[] scalar(ECPrivateKey key) {
        byte[] bytes = key.getS().toByteArray();
        byte[] result = new byte[32];
        int start = Math.max(0, bytes.length - 32);
        System.arraycopy(bytes, start, result, 32 - (bytes.length - start), bytes.length - start);
        return result;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void 개인키를_복원하면_같은_공개키와_짝이_맞는다() throws Exception {
        ECPrivateKey restored = VapidKeys.privateKey(base64Url(scalar((ECPrivateKey) keys.getPrivate())));
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(restored);
        signature.update(new byte[] {1, 2, 3});
        byte[] signed = signature.sign();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(VapidKeys.publicKey(publicKey));
        verifier.update(new byte[] {1, 2, 3});
        assertThat(verifier.verify(signed)).isTrue();
        assertThat(Arrays.equals(VapidKeys.uncompressed(VapidKeys.publicKey(publicKey)),
            Base64.getUrlDecoder().decode(publicKey))).isTrue();
    }
}
