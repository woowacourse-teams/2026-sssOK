package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gallery.organization.interpretation")
public record OrganizeInterpretationProperties(String apiKey, String model, Duration timeout,
                                               String reasoningEffort) {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

    public OrganizeInterpretationProperties {
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        reasoningEffort = isBlank(reasoningEffort) ? null : reasoningEffort.strip();
        if (isBlank(apiKey) || isBlank(model)) {
            throw new IllegalArgumentException("사진 정리 해석용 API 키와 모델 설정이 필요합니다");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("사진 정리 해석 제한 시간은 0초 초과 30초 이하여야 합니다");
        }
    }

    @Override
    public String toString() {
        return "OrganizeInterpretationProperties[apiKey=REDACTED, model=" + model + ", timeout=" + timeout
            + ", reasoningEffort=" + reasoningEffort + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
