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

    @Test
    void 만료_시각_이전이면_만료가_아니다() {
        LinkCode linkCode = LinkCode.issue(10L, new LinkCodeValue("483920"), NOW);

        assertThat(linkCode.isExpired(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    void 만료_시각이_지나면_만료다() {
        LinkCode linkCode = LinkCode.issue(10L, new LinkCodeValue("483920"), NOW);

        assertThat(linkCode.isExpired(NOW.plus(6, ChronoUnit.MINUTES))).isTrue();
    }

    @Test
    void 만료_시각과_정확히_같은_순간도_만료다() {
        LinkCode linkCode = LinkCode.issue(10L, new LinkCodeValue("483920"), NOW);

        assertThat(linkCode.isExpired(linkCode.getExpiresAt())).isTrue();
    }
}
