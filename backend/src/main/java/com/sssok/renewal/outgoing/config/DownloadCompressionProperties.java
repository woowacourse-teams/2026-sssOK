package com.sssok.renewal.outgoing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "download.compression")
public record DownloadCompressionProperties(Integer workers, Integer queueCapacity) {

    private static final int DEFAULT_WORKERS = 8;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;

    public DownloadCompressionProperties {
        workers = workers == null ? DEFAULT_WORKERS : workers;
        queueCapacity = queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        if (workers < 1) {
            throw new IllegalArgumentException("압축 워커 수는 1 이상이어야 합니다: " + workers);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("압축 대기열 크기는 0 이상이어야 합니다: " + queueCapacity);
        }
    }
}
