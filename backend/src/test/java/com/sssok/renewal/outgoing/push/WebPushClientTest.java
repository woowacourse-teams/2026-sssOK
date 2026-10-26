package com.sssok.renewal.outgoing.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.notification.PushSubscription;
import com.sssok.renewal.outgoing.config.WebPushProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 공개키·본문 단계에서 끝나는 경우만 다뤄 푸시 서비스로 HTTP 요청이 나가지 않는다.
class WebPushClientTest {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/test";

    private WebPushClient client;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(VapidKeys.p256Spec());
        KeyPair keys = generator.generateKeyPair();
        byte[] scalar = ((ECPrivateKey) keys.getPrivate()).getS().toByteArray();
        byte[] privateKey = new byte[32];
        int start = Math.max(0, scalar.length - 32);
        System.arraycopy(scalar, start, privateKey, 32 - (scalar.length - start), scalar.length - start);
        client = new WebPushClient(new WebPushProperties(
            BASE64_URL.encodeToString(VapidKeys.uncompressed((ECPublicKey) keys.getPublic())),
            BASE64_URL.encodeToString(privateKey), "mailto:test@sssok.test",
            Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void 곡선_위에_없는_구독_공개키는_만료로_돌려준다() {
        byte[] offCurveKey = new byte[65];
        offCurveKey[0] = 0x04;
        PushSubscription subscription = PushSubscription.create(1L, ENDPOINT,
            BASE64_URL.encodeToString(offCurveKey), BASE64_URL.encodeToString(new byte[16]));

        assertThat(client.send(subscription, "{}")).isEqualTo(PushSendResult.EXPIRED);
    }

    @Test
    void 구독과_상관없는_본문_오류는_만료로_보지_않는다() {
        KeyPair browserKeys = browserKeys();
        PushSubscription subscription = PushSubscription.create(1L, ENDPOINT,
            BASE64_URL.encodeToString(VapidKeys.uncompressed((ECPublicKey) browserKeys.getPublic())),
            BASE64_URL.encodeToString(new byte[16]));

        assertThatThrownBy(() -> client.send(subscription, "x".repeat(5000)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void VAPID_키가_없으면_보내지_않는다() {
        WebPushClient disabled = new WebPushClient(new WebPushProperties(
            "", "", "", Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(1)));
        PushSubscription subscription = PushSubscription.create(1L, ENDPOINT,
            BASE64_URL.encodeToString(new byte[65]), BASE64_URL.encodeToString(new byte[16]));

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.send(subscription, "{}")).isEqualTo(PushSendResult.SKIPPED);
    }

    private static KeyPair browserKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(VapidKeys.p256Spec());
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
