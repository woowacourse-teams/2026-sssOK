package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.url-cache")
public record MediaUrlCacheProperties(Boolean enabled, Long maximumSize, Duration refreshAhead) {
    public MediaUrlCacheProperties {
        enabled = enabled == null ? true : enabled;
        maximumSize = maximumSize == null ? 10_000L : maximumSize;
        refreshAhead = refreshAhead == null ? Duration.ofMinutes(2) : refreshAhead;
        if (maximumSize <= 0 || refreshAhead.isNegative()) {
            throw new IllegalArgumentException("URL 캐시 크기는 양수, 갱신 여유 시간은 0 이상이어야 합니다.");
        }
    }
}
