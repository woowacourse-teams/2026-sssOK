package com.sssok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class LinkCodeTest {

    private static final Instant NOW = Instant.parse("2026-08-22T04:10:00Z");

    @Test
    void 발급하면_5분_뒤_만료시각을_가진다() {
        LinkCode linkCode = LinkCode.issue(10L, new LinkCodeValue("483920"), NOW);

        assertThat(linkCode.getMemberId()).isEqualTo(10L);
        assertThat(linkCode.getCode().value()).isEqualTo("483920");
        assertThat(linkCode.getExpiresAt()).isEqualTo(NOW.plus(5, ChronoUnit.MINUTES));
    }

    @Test
    void 저장된_값으로_복원할_수_있다() {
        LinkCode linkCode = LinkCode.reconstruct(1L, 10L, new LinkCodeValue("483920"), NOW);

        assertThat(linkCode.getId()).isEqualTo(1L);
    }
}
