package com.sssok.infrastructure.config;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.search")
public record ImageSearchProperties(
    Instant createdSince,
    Integer concurrency,
    Integer queueCapacity,
    Integer batchSize,
    Integer maxAttempts,
    Duration requestTimeout,
    Duration retryDelay,
    Duration stuckAfter,
    Duration imageUrlTtl
) {
    public ImageSearchProperties {
        concurrency = concurrency == null ? 2 : concurrency;
        queueCapacity = queueCapacity == null ? 20 : queueCapacity;
        batchSize = batchSize == null ? 20 : batchSize;
        maxAttempts = maxAttempts == null ? 3 : maxAttempts;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        retryDelay = retryDelay == null ? Duration.ofMinutes(1) : retryDelay;
        stuckAfter = stuckAfter == null ? Duration.ofMinutes(3) : stuckAfter;
        imageUrlTtl = imageUrlTtl == null ? Duration.ofMinutes(5) : imageUrlTtl;
        requireWorkerLimits(concurrency, queueCapacity, batchSize, maxAttempts);
        requireTimeouts(requestTimeout, retryDelay, stuckAfter, imageUrlTtl);
    }

    private static void requireWorkerLimits(int concurrency, int queueCapacity, int batchSize, int maxAttempts) {
        if (concurrency < 1 || queueCapacity < 0 || batchSize < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("이미지 검색 작업 제한과 복구 시간을 확인해주세요");
        }
    }

    private static void requireTimeouts(Duration requestTimeout, Duration retryDelay,
        Duration stuckAfter, Duration imageUrlTtl) {
        if (!positive(requestTimeout) || !positive(retryDelay) || !positive(stuckAfter)
            || !positive(imageUrlTtl)) {
            throw new IllegalArgumentException("이미지 검색 작업 제한과 복구 시간을 확인해주세요");
        }
        // 설명 생성과 임베딩 호출이 모두 끝나기 전에 복구 배치가 재선점하지 않도록 한다.
        Duration minimumRecoveryDelay = requestTimeout.multipliedBy(2).plusSeconds(30);
        if (stuckAfter.compareTo(minimumRecoveryDelay) <= 0 || imageUrlTtl.compareTo(requestTimeout) <= 0) {
            throw new IllegalArgumentException("이미지 검색 작업 제한과 복구 시간을 확인해주세요");
        }
    }

    private static boolean positive(Duration value) {
        return !value.isNegative() && !value.isZero();
    }
}
