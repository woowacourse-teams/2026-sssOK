package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 키가 비어 있으면 발송만 건너뛰고 기동·API 는 그대로 된다.
@ConfigurationProperties(prefix = "notification.web-push")
public record WebPushProperties(
    String vapidPublicKey,
    String vapidPrivateKey,
    String subject,
    Duration ttl,
    Duration connectTimeout,
    Duration readTimeout
) {
    public boolean enabled() {
        return hasText(vapidPublicKey) && hasText(vapidPrivateKey) && hasText(subject);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
