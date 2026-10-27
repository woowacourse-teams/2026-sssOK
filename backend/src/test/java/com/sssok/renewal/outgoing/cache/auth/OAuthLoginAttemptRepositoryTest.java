package com.sssok.renewal.outgoing.cache.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.member.SocialProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


class OAuthLoginAttemptRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private final AtomicLong elapsedNanos = new AtomicLong();
    private final Cache<String, OAuthLoginAttempt> cache = Caffeine.newBuilder()
        .maximumSize(2)
        .expireAfterWrite(Duration.ofMinutes(5))
        .ticker(elapsedNanos::get)
        .executor(Runnable::run)
        .build();
    private final OAuthLoginAttemptRepository oauthLoginAttemptRepository = new OAuthLoginAttemptRepository(cache);

    @Test
    void 조회해도_유효시간이_연장되지_않고_5분에_만료한다() {
        OAuthLoginAttempt attempt = attempt(1);
        oauthLoginAttemptRepository.save(attempt);
        elapsedNanos.set(Duration.ofMinutes(4).toNanos());
        assertThat(oauthLoginAttemptRepository.findByStateHash(attempt.stateHash())).contains(attempt);
        elapsedNanos.set(Duration.ofMinutes(5).toNanos());
        assertThat(oauthLoginAttemptRepository.findByStateHash(attempt.stateHash())).isEmpty();
        assertThat(consume(attempt, NOW)).isFalse();
    }

    @Test
    void 잘못된_확인값으로_반복_접근해도_만료가_연장되지_않는다() {
        OAuthLoginAttempt attempt = attempt(1);
        oauthLoginAttemptRepository.save(attempt);
        elapsedNanos.set(Duration.ofMinutes(4).toNanos());
        assertThat(oauthLoginAttemptRepository.consume(attempt.stateHash(), "0".repeat(64), SocialProvider.KAKAO, NOW)).isFalse();
        elapsedNanos.set(Duration.ofMinutes(5).toNanos());
        assertThat(oauthLoginAttemptRepository.findByStateHash(attempt.stateHash())).isEmpty();
    }

    @Test
    void 저장_한도를_초과하면_일부_요청이_제거된다() {
        for (int i = 1; i <= 10; i++) {
            oauthLoginAttemptRepository.save(attempt(i));
        }
        cache.cleanUp();
        assertThat(cache.estimatedSize()).isEqualTo(2);
        int remaining = 0;
        for (int i = 1; i <= 10; i++) {
            if (consume(attempt(i), NOW)) {
                remaining++;
            }
        }
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void 잘못된_확인값과_제공자는_정상_요청을_지우지_않는다() {
        OAuthLoginAttempt attempt = attempt(1);
        oauthLoginAttemptRepository.save(attempt);
        assertThat(oauthLoginAttemptRepository.consume(attempt.stateHash(), "0".repeat(64), SocialProvider.KAKAO, NOW)).isFalse();
        assertThat(oauthLoginAttemptRepository.consume(attempt.stateHash(), attempt.loginVerifierHash(), SocialProvider.APPLE, NOW)).isFalse();
        assertThat(consume(attempt, NOW)).isTrue();
        assertThat(consume(attempt, NOW)).isFalse();
    }

    @Test
    void 캐시에_남아있어도_로그인_시도의_절대_만료시각에는_소비할_수_없다() {
        OAuthLoginAttempt attempt = attempt(1);
        oauthLoginAttemptRepository.save(attempt);
        assertThat(consume(attempt, attempt.expiresAt())).isFalse();
        assertThat(oauthLoginAttemptRepository.findByStateHash(attempt.stateHash())).isEmpty();
    }

    @Test
    void 재시작으로_새_저장소가_만들어지면_이전_요청은_사용할_수_없다() {
        OAuthLoginAttempt attempt = attempt(1);
        oauthLoginAttemptRepository.save(attempt);
        OAuthLoginAttemptRepository restarted = new OAuthLoginAttemptRepository();
        assertThat(restarted.findByStateHash(attempt.stateHash())).isEmpty();
    }

    @Test
    void 동시에_소비해도_한_요청만_성공한다() throws Exception {
        OAuthLoginAttempt attempt = attempt(1);
        oauthLoginAttemptRepository.save(attempt);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> consumeConcurrently(attempt, ready, start));
            Future<Integer> second = executor.submit(() -> consumeConcurrently(attempt, ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    private int consumeConcurrently(OAuthLoginAttempt attempt, CountDownLatch ready, CountDownLatch start)
        throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 대기 초과");
        }
        return consume(attempt, NOW) ? 1 : 0;
    }

    private boolean consume(OAuthLoginAttempt attempt, Instant now) {
        return oauthLoginAttemptRepository.consume(attempt.stateHash(), attempt.loginVerifierHash(), attempt.provider(), now);
    }

    private OAuthLoginAttempt attempt(int number) {
        return new OAuthLoginAttempt("%064x".formatted(number), "a".repeat(64), SocialProvider.KAKAO,
            "https://example.com/callback", null, List.of(), NOW, NOW.plusSeconds(300));
    }
}
