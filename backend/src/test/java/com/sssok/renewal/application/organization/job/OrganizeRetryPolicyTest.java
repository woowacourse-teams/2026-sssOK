package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy.Mode;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrganizeRetryPolicyTest {
    private static final Instant FINISHED = Instant.parse("2026-11-04T03:00:00Z");

    private final OrganizeRetryPolicy policy = new OrganizeRetryPolicy(
        new OrganizeJobProperties(true, null, null, null, null, null, null, null, null, null, null, null, null, "t"));

    private OrganizeJob job(OrganizeJobStatus status, OrganizeFailureCode code, int retryCount) {
        return new OrganizeJob(1L, UUID.randomUUID(), 10L, 20L, OrganizeKind.PERSON, OrganizeTrigger.BUTTON, status,
            null, "gallery", "group_photos_by_person", "설명", 5, 0, 3, retryCount,
            code == null ? null : code.name(), null, null, null, null, null, null, null, null, FINISHED,
            FINISHED, FINISHED);
    }

    @Test
    void 전달만_실패한_작업은_같은_실행으로_다시_보낸다() {
        assertThat(policy.modeOf(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.DISPATCH_TIMEOUT, 0)))
            .contains(Mode.RESEND);
    }

    @Test
    void 분석_서버가_실패로_끝낸_작업은_새_실행을_만든다() {
        assertThat(policy.modeOf(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.ANALYSIS_FAILED, 0)))
            .contains(Mode.REANALYZE);
    }

    @Test
    void 사용자_재시도는_작업당_3회까지다() {
        assertThat(policy.retryable(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.ANALYSIS_FAILED, 2))).isTrue();
        assertThat(policy.retryable(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.ANALYSIS_FAILED, 3))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrganizeFailureCode.class,
        names = {"NOT_STARTED", "LOST", "TIMEOUT", "AUTHENTICATION_FAILED", "TOOL_CHANGED", "TARGET_CHANGED",
            "RESULT_INVALID", "REJECTED"})
    void 원격_실행이_끝났는지_모르거나_다시_해도_소용없는_실패는_재시도할_수_없다(OrganizeFailureCode code) {
        assertThat(policy.retryable(job(OrganizeJobStatus.FAILED, code, 0))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrganizeJobStatus.class, names = {"QUEUED", "RUNNING", "READY", "CANCELED", "APPLIED", "EXPIRED"})
    void 실패하지_않은_작업은_재시도할_수_없다(OrganizeJobStatus status) {
        assertThat(policy.retryable(job(status, null, 0))).isFalse();
    }

    @Test
    void 재시도_가능_시각은_종료_시각에_횟수별_대기를_더한다() {
        assertThat(policy.retryAvailableAt(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.ANALYSIS_FAILED, 0)))
            .isEqualTo(FINISHED.plus(Duration.ofSeconds(5)));
        assertThat(policy.retryAvailableAt(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.ANALYSIS_FAILED, 2)))
            .isEqualTo(FINISHED.plus(Duration.ofSeconds(45)));
        assertThat(policy.retryAvailableAt(job(OrganizeJobStatus.FAILED, OrganizeFailureCode.LOST, 0))).isNull();
    }
}
