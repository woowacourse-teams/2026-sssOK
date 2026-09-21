package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 실행 스레드는 spring.task.execution 의 공용 워커 풀을 쓴다. 여기서는 회수 자체의 값만 갖는다.
@ConfigurationProperties(prefix = "storage.cleanup")
public record StorageCleanupProperties(
    // 이 시간이 지나도 대기열에 남아 있으면 커밋 직후의 비동기 정리가 실패했거나 유실된 것으로
    // 보고 다시 태운다. 진행 중인 정리를 배치가 가로채지 않을 만큼은 길어야 한다.
    Duration retryAfter,
    // 한 번에 회수할 개수. 밀린 오브젝트가 많아도 배치 한 번이 서버를 독점하지 않게 한다.
    Integer sweepBatchSize
) {

    public StorageCleanupProperties {
        retryAfter = retryAfter == null ? Duration.ofMinutes(5) : retryAfter;
        sweepBatchSize = sweepBatchSize == null ? 200 : sweepBatchSize;
    }
}
