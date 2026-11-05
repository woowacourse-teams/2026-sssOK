package com.sssok.renewal.application.organization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// 사진 정리의 만료·간격·재시도 대기를 테스트에서 직접 흘려보내기 위한 시계. 실제 시각에서 시작해 필요한 만큼만 앞으로 간다.
public class MutableClock extends Clock {
    // DB timestamp(6)과 정밀도를 맞춰 OS별 Instant.now() 정밀도 차이를 없앤다.
    private volatile Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public void reset() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
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
