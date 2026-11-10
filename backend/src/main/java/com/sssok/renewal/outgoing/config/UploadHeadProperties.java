package com.sssok.renewal.outgoing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업로드 완료 검증(HEAD)의 동시성·시간 제한.
 *
 * @param workers           서버 전체가 공유하는 HEAD 워커 수
 * @param queueCapacity     워커가 모두 바쁠 때 기다릴 수 있는 최대 작업 수. 넘으면 503
 * @param validationTimeout 한 요청의 파일 검증 전체(제출 + 대기) 제한
 * @param callTimeout       HEAD 호출 하나의 제한. 연결 획득과 SDK 재시도를 포함한다
 */
@ConfigurationProperties("media.upload.head")
public record UploadHeadProperties(Integer workers, Integer queueCapacity,
                                   Duration validationTimeout, Duration callTimeout) {

    private static final int DEFAULT_WORKERS = 4;
    private static final int MAX_WORKERS = 32;
    private static final int DEFAULT_QUEUE_CAPACITY = 64;
    private static final Duration DEFAULT_VALIDATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(3);

    public UploadHeadProperties {
        workers = workers == null ? DEFAULT_WORKERS : workers;
        queueCapacity = queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        validationTimeout = validationTimeout == null ? DEFAULT_VALIDATION_TIMEOUT : validationTimeout;
        callTimeout = callTimeout == null ? DEFAULT_CALL_TIMEOUT : callTimeout;
        if (workers < 1 || workers > MAX_WORKERS) {
            throw new IllegalArgumentException("media.upload.head.workers 는 1~" + MAX_WORKERS + " 이어야 한다: " + workers);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("media.upload.head.queue-capacity 는 0 이상이어야 한다: " + queueCapacity);
        }
        if (isNotPositive(validationTimeout) || isNotPositive(callTimeout)) {
            throw new IllegalArgumentException("media.upload.head 의 timeout 은 0보다 커야 한다");
        }
    }

    public static UploadHeadProperties defaults() {
        return new UploadHeadProperties(null, null, null, null);
    }

    private static boolean isNotPositive(Duration duration) {
        return duration.isNegative() || duration.isZero();
    }
}
