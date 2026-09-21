package com.sssok.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.video.worker")
public record VideoWorkerProperties(
    Integer coreSize,
    Integer maxSize,
    Integer queueCapacity
) {

    public VideoWorkerProperties {
        coreSize = coreSize == null ? 2 : coreSize;
        maxSize = maxSize == null ? 2 : maxSize;
        queueCapacity = queueCapacity == null ? 50 : queueCapacity;
    }
}
