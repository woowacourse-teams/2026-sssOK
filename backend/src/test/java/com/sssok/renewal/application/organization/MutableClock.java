package com.sssok.renewal.application.organization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

// 사진 정리의 만료·간격·재시도 대기를 테스트에서 직접 흘려보내기 위한 시계. 실제 시각에서 시작해 필요한 만큼만 앞으로 간다.
public class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    public void reset() {
        now = Instant.now();
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
