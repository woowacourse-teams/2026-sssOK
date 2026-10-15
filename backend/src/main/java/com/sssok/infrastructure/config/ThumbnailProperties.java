package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 파생본 URL 수명. 실행/재시도 정책은 ThumbnailQueueProperties가 담당한다.
@ConfigurationProperties(prefix = "media.thumbnail")
public record ThumbnailProperties(Duration displayUrlTtl) {
    public ThumbnailProperties {
        displayUrlTtl = displayUrlTtl == null ? Duration.ofMinutes(30) : displayUrlTtl;
    }
}
