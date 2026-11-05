package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrganizeRateLimiterTest {

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final OrganizeRateLimiter limiter = new OrganizeRateLimiter(
        new OrganizeJobProperties(true, null, null, null, null, null, null, null, null, null, null, null, 3, "token"), clock);

    @Test
    void 분당_허용_횟수를_넘으면_Retry_After와_함께_거절한다() {
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);

        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOfSatisfying(OrganizeRateLimitedException.class,
            e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 61L));
    }

    @Test
    void 회원마다_따로_센다() {
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);

        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }

    @Test
    void 일_분이_지나면_다시_허용한다() {
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);
        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }
}
