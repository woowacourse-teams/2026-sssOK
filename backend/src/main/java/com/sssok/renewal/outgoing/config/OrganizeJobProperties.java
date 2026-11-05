package com.sssok.renewal.outgoing.config;

import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizePhotoSource;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 정책으로 바뀔 수 있는 값(한도·보관·제한)을 한곳에 모았다. 정책이 정해지면 이 설정만 바꾼다.
@ConfigurationProperties(prefix = "gallery.organization.job")
public record OrganizeJobProperties(boolean enabled, Integer maxPhotos, Map<OrganizeKind, String> tools,
                                    Map<OrganizeKind, OrganizePhotoSource> photoSource, Duration photoUrlTtl,
                                    Dispatch dispatch, Liveness liveness, Duration retention, Duration review,
                                    Duration idempotencyRetention, Duration startInterval, Integer maxUserRetries,
                                    Integer rateLimitPerMinute, String internalToken) {
    private static final int DEFAULT_MAX_PHOTOS = 200;
    private static final int DEFAULT_RATE_LIMIT = 5;
    private static final int DEFAULT_MAX_USER_RETRIES = 3;

    public OrganizeJobProperties {
        maxPhotos = maxPhotos == null ? DEFAULT_MAX_PHOTOS : maxPhotos;
        tools = withDefaultTools(tools);
        photoSource = withDefaultSources(photoSource);
        photoUrlTtl = photoUrlTtl == null ? Duration.ofHours(1) : photoUrlTtl;
        dispatch = dispatch == null ? new Dispatch(null, null, null, null) : dispatch;
        liveness = liveness == null ? new Liveness(null, null, null) : liveness;
        // 결과 본문 보관 기한과 검토 기한은 분석 완료 시각부터 따로 센다.
        retention = retention == null ? Duration.ofDays(3) : retention;
        review = review == null ? Duration.ofMinutes(30) : review;
        idempotencyRetention = idempotencyRetention == null ? Duration.ofHours(24) : idempotencyRetention;
        startInterval = startInterval == null ? Duration.ofSeconds(60) : startInterval;
        maxUserRetries = maxUserRetries == null ? DEFAULT_MAX_USER_RETRIES : maxUserRetries;
        rateLimitPerMinute = rateLimitPerMinute == null ? DEFAULT_RATE_LIMIT : rateLimitPerMinute;
    }

    // 사용자 재시도 대기. 기존 발송 백오프의 앞 3단계(5초·15초·45초)를 재시도 횟수 순서로 적용한다.
    public Duration userRetryDelay(int retryCount) {
        List<Duration> delays = dispatch.retryDelays();
        int index = Math.min(Math.max(retryCount, 0), Math.min(2, delays.size() - 1));
        return delays.get(index);
    }

    public record Dispatch(Duration deadline, Duration sweepInterval, Duration lease, List<Duration> retryDelays) {
        public Dispatch {
            deadline = deadline == null ? Duration.ofMinutes(15) : deadline;
            sweepInterval = sweepInterval == null ? Duration.ofSeconds(10) : sweepInterval;
            lease = lease == null ? Duration.ofMinutes(2) : lease;
            retryDelays = retryDelays == null || retryDelays.isEmpty()
                ? List.of(Duration.ofSeconds(5), Duration.ofSeconds(15), Duration.ofSeconds(45),
                    Duration.ofMinutes(2), Duration.ofMinutes(5))
                : List.copyOf(retryDelays);
        }

        // 시도 횟수가 늘어도 마지막 간격을 유지한다.
        public Duration retryDelayAfter(int attempts) {
            int index = Math.min(Math.max(attempts, 1), retryDelays.size()) - 1;
            return retryDelays.get(index);
        }
    }

    public record Liveness(Duration startDeadline, Duration heartbeatTimeout, Duration runLimit) {
        public Liveness {
            startDeadline = startDeadline == null ? Duration.ofMinutes(30) : startDeadline;
            heartbeatTimeout = heartbeatTimeout == null ? Duration.ofMinutes(3) : heartbeatTimeout;
            runLimit = runLimit == null ? Duration.ofMinutes(15) : runLimit;
        }
    }

    public String toolNameOf(OrganizeKind kind) {
        return tools.get(kind);
    }

    public OrganizeKind kindOf(String toolName) {
        return tools.entrySet().stream()
            .filter(entry -> entry.getValue().equals(toolName))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    public OrganizePhotoSource photoSourceOf(OrganizeKind kind) {
        return photoSource.get(kind);
    }

    // ConfigurationPropertiesScan 이 기능이 꺼져 있어도 이 객체를 만든다. 켜는 시점에만 필수 값을 검사한다.
    public void requireValid() {
        if (maxPhotos < 1 || photoUrlTtl.isZero() || photoUrlTtl.isNegative() || rateLimitPerMinute < 1
            || review.isZero() || review.isNegative() || idempotencyRetention.isZero()
            || idempotencyRetention.isNegative() || startInterval.isNegative() || maxUserRetries < 0) {
            throw new IllegalArgumentException("사진 정리 작업 설정을 확인해주세요");
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalArgumentException("사진 정리 내부 이벤트 토큰이 필요합니다");
        }
    }

    @Override
    public String toString() {
        return "OrganizeJobProperties[enabled=" + enabled + ", maxPhotos=" + maxPhotos + ", internalToken=REDACTED]";
    }

    private static Map<OrganizeKind, String> withDefaultTools(Map<OrganizeKind, String> configured) {
        Map<OrganizeKind, String> tools = new EnumMap<>(OrganizeKind.class);
        tools.put(OrganizeKind.PERSON, "group_photos_by_person");
        tools.put(OrganizeKind.SIMILAR, "group_similar_photos");
        if (configured != null) {
            tools.putAll(configured);
        }
        return Map.copyOf(tools);
    }

    private static Map<OrganizeKind, OrganizePhotoSource> withDefaultSources(
        Map<OrganizeKind, OrganizePhotoSource> configured) {
        Map<OrganizeKind, OrganizePhotoSource> sources = new EnumMap<>(OrganizeKind.class);
        for (OrganizeKind kind : OrganizeKind.values()) {
            sources.put(kind, OrganizePhotoSource.PREVIEW);
        }
        if (configured != null) {
            sources.putAll(configured);
        }
        return Map.copyOf(sources);
    }
}
