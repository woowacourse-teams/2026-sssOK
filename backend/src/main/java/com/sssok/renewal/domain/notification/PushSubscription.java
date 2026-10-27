package com.sssok.renewal.domain.notification;

import com.sssok.renewal.domain.notification.exception.InvalidPushSubscriptionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import lombok.Getter;

// 브라우저가 PushManager.subscribe() 로 만든 구독 정보. 서버는 endpoint 로 직접 HTTP 요청을 보내므로,
// 사용자가 보낸 임의 주소로 요청이 나가지 않게(SSRF) 알려진 브라우저 푸시 서비스 호스트만 받는다.
@Getter
public class PushSubscription {
    private static final int MAX_ENDPOINT_LENGTH = 1024;
    // 비압축 P-256 공개키(0x04 + X 32바이트 + Y 32바이트)
    private static final int P256DH_LENGTH = 65;
    private static final int AUTH_LENGTH = 16;
    private static final List<String> PUSH_SERVICE_HOSTS = List.of(
        "fcm.googleapis.com",            // Chrome·Edge(안드로이드 포함)
        "push.services.mozilla.com",     // Firefox
        "push.apple.com",                // Safari
        "notify.windows.com"             // 구형 Edge
    );

    private final Long memberId;
    private final String endpoint;
    private final String p256dh;
    private final String auth;

    private PushSubscription(Long memberId, String endpoint, String p256dh, String auth) {
        this.memberId = memberId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static PushSubscription create(Long memberId, String endpoint, String p256dh, String auth) {
        validateEndpoint(endpoint);
        validateKey("p256dh", p256dh, P256DH_LENGTH);
        validateKey("auth", auth, AUTH_LENGTH);
        return new PushSubscription(memberId, endpoint, p256dh, auth);
    }

    public static PushSubscription reconstruct(Long memberId, String endpoint, String p256dh, String auth) {
        return new PushSubscription(memberId, endpoint, p256dh, auth);
    }

    public byte[] p256dhBytes() {
        return Base64.getUrlDecoder().decode(p256dh);
    }

    public byte[] authBytes() {
        return Base64.getUrlDecoder().decode(auth);
    }

    private static void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new InvalidPushSubscriptionException("endpoint 값이 올바르지 않습니다");
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new InvalidPushSubscriptionException("endpoint 값이 올바르지 않습니다");
        }
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null || !isPushServiceHost(host)) {
            throw new InvalidPushSubscriptionException("지원하지 않는 푸시 서비스입니다");
        }
    }

    private static boolean isPushServiceHost(String host) {
        return PUSH_SERVICE_HOSTS.stream()
            .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }

    private static void validateKey(String name, String value, int expectedLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidPushSubscriptionException(name + " 값이 비어 있습니다");
        }
        try {
            // 브라우저는 base64url 로 주지만 패딩 유무가 섞여 있어 둘 다 받는다.
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != expectedLength) {
                throw new InvalidPushSubscriptionException(name + " 값의 길이가 올바르지 않습니다");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidPushSubscriptionException(name + " 값의 형식이 올바르지 않습니다");
        }
    }
}
