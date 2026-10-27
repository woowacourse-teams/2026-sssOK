package com.sssok.renewal.outgoing.cache.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.member.SocialProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthLoginAttemptRepository {
    private final Cache<String, OAuthLoginAttempt> attempts;

    public OAuthLoginAttemptRepository() {
        this(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .scheduler(Scheduler.systemScheduler())
            .build());
    }

    OAuthLoginAttemptRepository(Cache<String, OAuthLoginAttempt> attempts) {
        this.attempts = attempts;
    }

    public void save(OAuthLoginAttempt attempt) {
        attempts.put(attempt.stateHash(), attempt);
    }

    public Optional<OAuthLoginAttempt> findByStateHash(String stateHash) {
        return Optional.ofNullable(attempts.getIfPresent(stateHash));
    }

    // 불변 데이터를 검증한 뒤 그 값이 그대로 있을 때만 원자적으로 제거한다.
    // 잘못된 확인값은 정상 요청을 지우거나 유효시간을 연장하지 않는다.
    public boolean consume(String stateHash, String loginVerifierHash, SocialProvider provider, Instant now) {
        OAuthLoginAttempt attempt = attempts.getIfPresent(stateHash);
        if (attempt == null) {
            return false;
        }
        if (attempt.isExpiredAt(now)) {
            attempts.asMap().remove(stateHash, attempt);
            return false;
        }
        if (attempt.provider() != provider || !attempt.loginVerifierHash().equals(loginVerifierHash)) {
            return false;
        }
        return attempts.asMap().remove(stateHash, attempt);
    }
}
