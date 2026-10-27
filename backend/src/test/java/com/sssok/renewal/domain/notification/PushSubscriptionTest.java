package com.sssok.renewal.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.notification.exception.InvalidPushSubscriptionException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushSubscriptionTest {

    static final String P256DH = Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressedKey());
    static final String AUTH = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);

    static byte[] uncompressedKey() {
        byte[] key = new byte[65];
        key[0] = 0x04;
        return key;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://fcm.googleapis.com/fcm/send/abc",
        "https://updates.push.services.mozilla.com/wpush/v2/abc",
        "https://web.push.apple.com/abc",
        "https://wns2-par02p.notify.windows.com/w/?token=abc"
    })
    void 브라우저_푸시_서비스_주소는_받는다(String endpoint) {
        PushSubscription subscription = PushSubscription.create(1L, endpoint, P256DH, AUTH);

        assertThat(subscription.getEndpoint()).isEqualTo(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://fcm.googleapis.com/fcm/send/abc",
        "https://evil.example.com/fcm.googleapis.com",
        "https://fcm.googleapis.com.evil.example.com/abc",
        "https://localhost:8080/internal",
        "https://169.254.169.254/latest/meta-data",
        "not a url"
    })
    void 푸시_서비스가_아닌_주소는_거절한다(String endpoint) {
        assertThatThrownBy(() -> PushSubscription.create(1L, endpoint, P256DH, AUTH))
            .isInstanceOf(InvalidPushSubscriptionException.class);
    }

    @Test
    void 패딩이_붙은_base64url_키도_받는다() {
        String padded = Base64.getUrlEncoder().encodeToString(uncompressedKey());

        PushSubscription subscription = PushSubscription.create(1L, "https://fcm.googleapis.com/x", padded, AUTH);

        assertThat(subscription.p256dhBytes()).hasSize(65);
    }

    @Test
    void 키_길이가_틀리면_거절한다() {
        String shortKey = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[10]);

        assertThatThrownBy(() -> PushSubscription.create(1L, "https://fcm.googleapis.com/x", shortKey, AUTH))
            .isInstanceOf(InvalidPushSubscriptionException.class);
        assertThatThrownBy(() -> PushSubscription.create(1L, "https://fcm.googleapis.com/x", P256DH, shortKey))
            .isInstanceOf(InvalidPushSubscriptionException.class);
    }

    @Test
    void base64url이_아닌_키는_거절한다() {
        assertThatThrownBy(() -> PushSubscription.create(1L, "https://fcm.googleapis.com/x", "!!!", AUTH))
            .isInstanceOf(InvalidPushSubscriptionException.class);
    }
}
