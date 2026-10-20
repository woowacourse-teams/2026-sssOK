package com.sssok.renewal.domain.media.thumbnail;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public record ThumbnailRetryPolicy(int maxAttempts, Duration retryBase, Duration retryMax) {
    public boolean shouldStop(int attempts, boolean permanentFailure) {
        return permanentFailure || attempts >= maxAttempts;
    }

    public Duration nextDelay(int attempts) {
        long maximumDelayMillis = retryBase.toMillis();
        for (int attempt = 1; attempt < attempts && maximumDelayMillis < retryMax.toMillis(); attempt++) {
            maximumDelayMillis = Math.min(retryMax.toMillis(), maximumDelayMillis * 2);
        }
        // 기존 equal jitter 범위: 지수 백오프 상한의 절반부터 상한까지.
        long delayMillis = ThreadLocalRandom.current().nextLong(
            Math.max(1, maximumDelayMillis / 2), maximumDelayMillis + 1);
        return Duration.ofMillis(delayMillis);
    }
}
