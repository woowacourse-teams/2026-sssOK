package com.sssok.renewal.domain.organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizeExecution(Long id, Long jobId, UUID executionId, int attempt,
                                OrganizeExecutionStatus status, int dispatchAttempts, Instant dispatchDeadlineAt,
                                Instant acceptedAt, Instant startedAt, Instant lastEventAt, Instant finishedAt) {

    // 분석 서버가 이 실행을 받았을 수 있는지. 확인받았거나, 보내는 중이거나, 받았는지 모르는 상태이면 받았을 수 있다.
    public boolean mayHaveReachedAnalysis() {
        return acceptedAt != null
            || status == OrganizeExecutionStatus.DISPATCHING
            || status == OrganizeExecutionStatus.DISPATCH_UNKNOWN
            || status == OrganizeExecutionStatus.RUNNING;
    }
}
