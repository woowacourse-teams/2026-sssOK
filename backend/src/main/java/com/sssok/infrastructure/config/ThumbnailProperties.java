package com.sssok.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 파생본을 만드는 일 자체가 아니라, 그 일이 밀렸을 때 회수하고 결과를 화면에 내주는 값들.
// 해상도·품질·포맷은 DerivativeImageProperties(media.image) 가 갖는다.
//
// 실행 스레드는 spring.task.execution 의 공용 워커 풀을 쓴다.
@ConfigurationProperties(prefix = "media.thumbnail")
public record ThumbnailProperties(
    // 이 시간이 지나도 PROCESSING 이면 워커가 죽었거나 서버가 재시작된 것으로 보고 다시 태운다.
    Duration stuckAfter,
    // 한 번에 회수할 개수. 밀린 작업이 많아도 배치 한 번이 서버를 독점하지 않게 한다.
    Integer sweepBatchSize,
    // 조회 응답에 직접 싣는 파생본 서명 URL의 유효기간. 다운로드용(5분)보다 길게 잡아,
    // 목록 화면을 잠시 보고 있어도 썸네일이 깨지지 않게 한다. 프리뷰도 같은 값을 쓴다.
    Duration displayUrlTtl
) {

    public ThumbnailProperties {
        stuckAfter = stuckAfter == null ? Duration.ofMinutes(10) : stuckAfter;
        sweepBatchSize = sweepBatchSize == null ? 50 : sweepBatchSize;
        displayUrlTtl = displayUrlTtl == null ? Duration.ofMinutes(30) : displayUrlTtl;
    }
}
