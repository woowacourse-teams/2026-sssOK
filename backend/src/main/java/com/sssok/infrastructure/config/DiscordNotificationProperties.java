package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.discord")
public record DiscordNotificationProperties(
    // 새 의견을 받을 채널의 웹훅 URL. 비어 있으면 알림을 건너뛴다 — 로컬·테스트처럼 채널이 없는
    // 환경에서도 기동과 의견 등록이 그대로 돼야 한다.
    String feedbackWebhookUrl,
    Duration connectTimeout,
    // 디스코드가 응답하지 않을 때 워커 스레드를 붙잡고 있는 최대 시간. 공용 풀이라 짧게 둔다.
    Duration readTimeout
) {

    public DiscordNotificationProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }

    public boolean hasFeedbackWebhook() {
        return feedbackWebhookUrl != null && !feedbackWebhookUrl.isBlank();
    }
}
