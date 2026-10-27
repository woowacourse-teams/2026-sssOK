package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 서비스 계정 키가 비어 있으면 앱 알림 발송만 건너뛰고 기동·API 는 그대로 된다.
@ConfigurationProperties(prefix = "notification.fcm")
public record FcmProperties(
    String serviceAccount,
    String apiBaseUrl,
    Duration ttl,
    Duration connectTimeout,
    Duration readTimeout
) {
    public boolean enabled() {
        return serviceAccount != null && !serviceAccount.isBlank();
    }
}
