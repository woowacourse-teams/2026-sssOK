package com.sssok.renewal.domain.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizeExecutionTest {
    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    void 아직_보내지_않은_실행은_분석_서버에_닿지_않았다() {
        assertThat(execution(OrganizeExecutionStatus.DISPATCH_PENDING, null).mayHaveReachedAnalysis()).isFalse();
    }

    @Test
    void 보내는_중이거나_받았는지_모르는_실행은_닿았을_수_있다() {
        assertThat(execution(OrganizeExecutionStatus.DISPATCHING, null).mayHaveReachedAnalysis()).isTrue();
        assertThat(execution(OrganizeExecutionStatus.DISPATCH_UNKNOWN, null).mayHaveReachedAnalysis()).isTrue();
        assertThat(execution(OrganizeExecutionStatus.RUNNING, null).mayHaveReachedAnalysis()).isTrue();
    }

    @Test
    void 분석_서버가_받았다고_확인한_실행은_닿았다() {
        assertThat(execution(OrganizeExecutionStatus.DISPATCH_PENDING, NOW).mayHaveReachedAnalysis()).isTrue();
    }

    private OrganizeExecution execution(OrganizeExecutionStatus status, Instant acceptedAt) {
        return new OrganizeExecution(1L, 1L, UUID.randomUUID(), 1, status, 0, NOW, acceptedAt, null, null, null);
    }
}
