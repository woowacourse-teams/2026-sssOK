package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.thumbnail.queue")
public record ThumbnailQueueProperties(
    int imageWorkers,
    int videoWorkers,
    int maxAttempts,
    Duration lease,
    Duration maxRuntime,
    Duration retryBase,
    Duration retryMax,
    int recoveryBatchSize
) {
    private static final int DEFAULT_IMAGE_WORKERS = 4;
    private static final int DEFAULT_VIDEO_WORKERS = 2;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RECOVERY_BATCH_SIZE = 50;
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);
    private static final Duration DEFAULT_MAX_RUNTIME = Duration.ofMinutes(10);
    private static final Duration DEFAULT_RETRY_BASE = Duration.ofSeconds(5);
    private static final Duration DEFAULT_RETRY_MAX = Duration.ofMinutes(5);

    public ThumbnailQueueProperties {
        imageWorkers = imageWorkers == 0 ? DEFAULT_IMAGE_WORKERS : imageWorkers;
        videoWorkers = videoWorkers == 0 ? DEFAULT_VIDEO_WORKERS : videoWorkers;
        maxAttempts = maxAttempts == 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
        lease = lease == null ? DEFAULT_LEASE : lease;
        maxRuntime = maxRuntime == null ? DEFAULT_MAX_RUNTIME : maxRuntime;
        retryBase = retryBase == null ? DEFAULT_RETRY_BASE : retryBase;
        retryMax = retryMax == null ? DEFAULT_RETRY_MAX : retryMax;
        recoveryBatchSize = recoveryBatchSize == 0 ? DEFAULT_RECOVERY_BATCH_SIZE : recoveryBatchSize;

        validateWorkerCounts(imageWorkers, videoWorkers);
        validateLeaseSettings(lease, maxRuntime);
        validateRetrySettings(maxAttempts, retryBase, retryMax);
        requirePositive(recoveryBatchSize, "recovery-batch-size");
    }

    // 기존 밀리초 단위 계산을 유지한다. 임대 시간의 1/3마다 생존 여부를 갱신한다.
    public Duration heartbeatInterval() {
        return Duration.ofMillis(lease.toMillis() / 3);
    }

    private static void validateWorkerCounts(int imageWorkers, int videoWorkers) {
        requirePositive(imageWorkers, "image-workers");
        requirePositive(videoWorkers, "video-workers");
    }

    private static void validateLeaseSettings(Duration lease, Duration maxRuntime) {
        if (lease.toMillis() < 3000) {
            throw new IllegalArgumentException("썸네일 큐 lease는 3초 이상이어야 합니다");
        }
        if (maxRuntime.compareTo(lease) < 0) {
            throw new IllegalArgumentException("썸네일 큐 max-runtime은 lease 이상이어야 합니다");
        }
    }

    private static void validateRetrySettings(int maxAttempts, Duration retryBase, Duration retryMax) {
        requirePositive(maxAttempts, "max-attempts");
        if (retryBase.isNegative() || retryBase.isZero()) {
            throw new IllegalArgumentException("썸네일 큐 retry-base는 0보다 커야 합니다");
        }
        if (retryMax.compareTo(retryBase) < 0) {
            throw new IllegalArgumentException("썸네일 큐 retry-max는 retry-base 이상이어야 합니다");
        }
    }

    private static void requirePositive(int value, String settingName) {
        if (value < 1) {
            throw new IllegalArgumentException("썸네일 큐 " + settingName + "는 1 이상이어야 합니다");
        }
    }
}
