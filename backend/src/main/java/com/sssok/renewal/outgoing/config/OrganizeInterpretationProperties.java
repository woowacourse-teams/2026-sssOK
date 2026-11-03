package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gallery.organization.interpretation")
public record OrganizeInterpretationProperties(String apiKey, String model, Duration timeout) {
    public OrganizeInterpretationProperties {
        timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("사진 정리 해석용 API 키와 모델 설정이 필요합니다");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("사진 정리 해석 제한 시간은 0초 초과 30초 이하여야 합니다");
        }
    }

    @Override
    public String toString() {
        return "OrganizeInterpretationProperties[apiKey=REDACTED, model=" + model + ", timeout=" + timeout + "]";
    }
}
