package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 실행 스레드는 spring.task.execution 의 공용 워커 풀을 쓴다. 여기서는 썸네일 자체의 값만 갖는다.
@ConfigurationProperties(prefix = "media.thumbnail")
public record ThumbnailProperties(
    Integer maxWidth,
    // 이 시간이 지나도 PROCESSING 이면 워커가 죽었거나 서버가 재시작된 것으로 보고 다시 태운다.
    Duration stuckAfter,
    // 한 번에 회수할 개수. 밀린 작업이 많아도 배치 한 번이 서버를 독점하지 않게 한다.
    Integer sweepBatchSize
) {

    public ThumbnailProperties {
        maxWidth = maxWidth == null ? 400 : maxWidth;
        stuckAfter = stuckAfter == null ? Duration.ofMinutes(10) : stuckAfter;
        sweepBatchSize = sweepBatchSize == null ? 50 : sweepBatchSize;
    }
}
