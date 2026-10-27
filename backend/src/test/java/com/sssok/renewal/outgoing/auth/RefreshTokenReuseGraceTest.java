package com.sssok.renewal.outgoing.auth;

import com.sssok.renewal.outgoing.database.auth.RefreshTokenJpaEntity;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenReuseGraceTest {

    @ParameterizedTest
    @CsvSource({"-1, false", "0, true", "4999999999, true", "5000000000, false"})
    void 유예는_소비시각부터_5초_미만까지_적용한다(long elapsedNanos, boolean expected) {
        Instant usedAt = Instant.parse("2026-10-26T00:00:00Z");
        RefreshTokenJpaEntity token = new RefreshTokenJpaEntity("hash", "session");
        token.consume(usedAt, "replacement-hash");

        assertThat(token.isWithinReuseGrace(usedAt.plusNanos(elapsedNanos), Duration.ofSeconds(5)))
            .isEqualTo(expected);
    }
}
