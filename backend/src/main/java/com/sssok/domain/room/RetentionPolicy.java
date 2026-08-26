package com.sssok.domain.room;

import java.time.Duration;
import java.time.Instant;

// 수명을 다한 방을 얼마나 보관했다가 영구 삭제할지 정한다.
// 방장이 지운 방과 그냥 만료된 방을 같은 기간으로 다룬다.
public class RetentionPolicy {

    private static final Duration RETENTION_AFTER_END = Duration.ofDays(7);

    // 이 시각보다 먼저 끝난 방이 영구 삭제 대상이다.
    public Instant threshold(Instant now) {
        return now.minus(RETENTION_AFTER_END);
    }

    public Instant purgeAt(Instant endedAt) {
        return endedAt.plus(RETENTION_AFTER_END);
    }
}
