package com.sssok.renewal.application.organization.job;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 모델을 부르는 문장 요청의 회원별 분당 호출 제한. 메모리 방식이라 서버 1대를 전제로 한다.
@Component
public class OrganizeRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final OrganizeJobProperties properties;
    private final Clock clock;
    private final Cache<Long, Deque<Instant>> calls = Caffeine.newBuilder()
        .expireAfterAccess(WINDOW.multipliedBy(2))
        .maximumSize(100_000)
        .build();

    @Autowired
    public OrganizeRateLimiter(OrganizeJobProperties properties) {
        this(properties, Clock.systemUTC());
    }

    OrganizeRateLimiter(OrganizeJobProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void acquire(Long memberId) {
        Deque<Instant> history = calls.get(memberId, id -> new ArrayDeque<>());
        synchronized (history) {
            Instant now = clock.instant();
            while (!history.isEmpty() && !history.peekFirst().plus(WINDOW).isAfter(now)) {
                history.removeFirst();
            }
            if (history.size() >= properties.rateLimitPerMinute()) {
                long retryAfter = Duration.between(now, history.peekFirst().plus(WINDOW)).toSeconds() + 1;
                throw new OrganizeRateLimitedException(retryAfter);
            }
            history.addLast(now);
        }
    }
}
