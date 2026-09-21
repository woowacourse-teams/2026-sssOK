package com.sssok.application.admin;

import com.sssok.application.admin.exception.AdminLoginRateLimitedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 로그인 실패 횟수를 아이디별로 세어 일정 횟수를 넘으면 잠시 막는다.
// 비밀번호를 하나씩 대입해 보는 시도를 늦추는 게 목적이다.
@Component
public class LoginAttemptLimiter {

    private final Map<String, Attempts> attemptsByLoginId = new ConcurrentHashMap<>();

    @Value("${admin.login.max-failures:5}")
    private int maxFailures;

    @Value("${admin.login.lock-duration:5m}")
    private Duration lockDuration;

    public void requireNotLocked(String loginId, Instant now) {
        Attempts attempts = attemptsByLoginId.get(loginId);
        if (attempts == null || !attempts.isLocked(now, maxFailures)) {
            return;
        }
        long retryAfterSeconds = Math.max(1, Duration.between(now, attempts.lockedUntil).toSeconds());
        throw new AdminLoginRateLimitedException(retryAfterSeconds);
    }

    public void recordFailure(String loginId, Instant now) {
        attemptsByLoginId.compute(loginId, (key, current) -> {
            Attempts attempts = current == null ? new Attempts() : current;
            attempts.count++;
            if (attempts.count >= maxFailures) {
                attempts.lockedUntil = now.plus(lockDuration);
            }
            return attempts;
        });
    }

    public void reset(String loginId) {
        attemptsByLoginId.remove(loginId);
    }

    private static final class Attempts {

        private int count;
        private Instant lockedUntil;

        private boolean isLocked(Instant now, int maxFailures) {
            if (lockedUntil == null) {
                return false;
            }
            if (now.isBefore(lockedUntil)) {
                return true;
            }
            count = 0;
            lockedUntil = null;
            return false;
        }
    }
}
