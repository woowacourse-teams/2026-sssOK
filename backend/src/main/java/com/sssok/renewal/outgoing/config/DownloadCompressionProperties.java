package com.sssok.renewal.outgoing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// prefetchDepth: 압축 한 건이 미리 겹쳐 열어 둘 다음 원본 수(#666). 0 이면 미리 열지 않는다.
// 워커 수 × depth 만큼 스토리지 GET 커넥션을 더 쓴다(기본 8 × 3 = 24, SDK 기본 커넥션 상한 50).
@ConfigurationProperties(prefix = "download.compression")
public record DownloadCompressionProperties(Integer workers, Integer queueCapacity, Integer prefetchDepth) {

    private static final int DEFAULT_WORKERS = 8;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final int DEFAULT_PREFETCH_DEPTH = 3;

    public DownloadCompressionProperties {
        workers = workers == null ? DEFAULT_WORKERS : workers;
        queueCapacity = queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        prefetchDepth = prefetchDepth == null ? DEFAULT_PREFETCH_DEPTH : prefetchDepth;
        if (workers < 1) {
            throw new IllegalArgumentException("압축 워커 수는 1 이상이어야 합니다: " + workers);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("압축 대기열 크기는 0 이상이어야 합니다: " + queueCapacity);
        }
        if (prefetchDepth < 0) {
            throw new IllegalArgumentException("미리 열 원본 수는 0 이상이어야 합니다: " + prefetchDepth);
        }
    }
}
