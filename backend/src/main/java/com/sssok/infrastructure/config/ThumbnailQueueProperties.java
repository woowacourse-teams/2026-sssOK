package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.thumbnail.queue")
public record ThumbnailQueueProperties(int imageWorkers, int videoWorkers, int maxAttempts,
                                       Duration lease, Duration maxRuntime,
                                       Duration retryBase, Duration retryMax, int recoveryBatchSize) {
    public ThumbnailQueueProperties {
        imageWorkers = imageWorkers == 0 ? 4 : imageWorkers;
        videoWorkers = videoWorkers == 0 ? 2 : videoWorkers;
        maxAttempts = maxAttempts == 0 ? 5 : maxAttempts;
        lease = lease == null ? Duration.ofMinutes(2) : lease;
        maxRuntime = maxRuntime == null ? Duration.ofMinutes(10) : maxRuntime;
        retryBase = retryBase == null ? Duration.ofSeconds(5) : retryBase;
        retryMax = retryMax == null ? Duration.ofMinutes(5) : retryMax;
        recoveryBatchSize = recoveryBatchSize == 0 ? 50 : recoveryBatchSize;
        if (imageWorkers < 1 || videoWorkers < 1 || maxAttempts < 1 || recoveryBatchSize < 1
            || lease.toMillis() < 3000 || maxRuntime.compareTo(lease) < 0
            || retryBase.isNegative() || retryBase.isZero() || retryMax.compareTo(retryBase) < 0) {
            throw new IllegalArgumentException("썸네일 큐 설정 범위를 확인하세요");
        }
    }
}
