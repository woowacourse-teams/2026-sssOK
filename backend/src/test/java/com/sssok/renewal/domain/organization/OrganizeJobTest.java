package com.sssok.renewal.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizeJobTest {
    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");

    private OrganizeJob job(OrganizeJobStatus status, String result, Instant reviewExpiresAt, Instant resultExpiresAt) {
        return new OrganizeJob(1L, UUID.randomUUID(), 10L, 20L, OrganizeKind.PERSON, OrganizeTrigger.BUTTON, status,
            null, "gallery", "tool", "설명", 5, 0, 1, 0, null, null, 1, result, null, null, null, null, reviewExpiresAt,
            resultExpiresAt, null, NOW, NOW);
    }

    @Test
    void 요청자만_자기_작업이다() {
        OrganizeJob job = job(OrganizeJobStatus.QUEUED, null, null, null);

        assertThat(job.isOwnedBy(20L)).isTrue();
        assertThat(job.isOwnedBy(21L)).isFalse();
    }

    @Test
    void READY는_검토_기한에_닿는_순간부터_기한_초과다() {
        Instant expires = NOW.plus(Duration.ofMinutes(30));
        OrganizeJob job = job(OrganizeJobStatus.READY, "{}", expires, NOW.plus(Duration.ofDays(3)));

        assertThat(job.isReviewOverdue(expires.minusSeconds(1))).isFalse();
        assertThat(job.isReviewOverdue(expires)).isTrue();
        assertThat(job.isReviewOverdue(expires.plusSeconds(1))).isTrue();
    }

    @Test
    void READY가_아니면_검토_기한이_있어도_기한_초과가_아니다() {
        Instant past = NOW.minusSeconds(1);

        assertThat(job(OrganizeJobStatus.RUNNING, null, null, null).isReviewOverdue(NOW)).isFalse();
        assertThat(job(OrganizeJobStatus.APPLIED, "{}", past, NOW.plusSeconds(60)).isReviewOverdue(NOW)).isFalse();
        assertThat(job(OrganizeJobStatus.EXPIRED, "{}", past, NOW.plusSeconds(60)).isReviewOverdue(NOW)).isFalse();
    }

    @Test
    void 결과_본문이_없거나_보관_기한이_지나면_결과가_만료다() {
        Instant expires = NOW.plus(Duration.ofDays(3));

        assertThat(job(OrganizeJobStatus.APPLIED, "{}", NOW, expires).isResultExpired(expires.minusSeconds(1))).isFalse();
        assertThat(job(OrganizeJobStatus.APPLIED, "{}", NOW, expires).isResultExpired(expires)).isTrue();
        assertThat(job(OrganizeJobStatus.APPLIED, null, NOW, expires).isResultExpired(NOW)).isTrue();
        assertThat(job(OrganizeJobStatus.APPLIED, "{}", NOW, expires).hasResult()).isTrue();
    }
}
