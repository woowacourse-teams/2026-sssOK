package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

// 마지막 생존 시각과 기한으로 중단·유실을 감지한다. 모두 자동 재분석 없이 FAILED 로 닫고,
// 이후 늦게 온 결과는 이벤트 수신에서 폐기하고 확인한다. 검토 기한이 지난 READY 를 만료시키고 보관 기한이 지난 결과 본문과 멱등 기록도 여기서 정리한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizeLivenessService {
    private final OrganizeJobProperties properties;
    private final OrganizeJobRepository repository;
    private final OrganizeJobTransitions transitions;
    private final OrganizeJobExpiry expiry;
    @Qualifier("organizeClock")
    private final Clock clock;

    public void sweep() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        OrganizeJobProperties.Liveness liveness = properties.liveness();
        failAll(repository.findDispatchExpired(now), OrganizeFailureCode.DISPATCH_TIMEOUT);
        failAll(repository.findNotStarted(now.minus(liveness.startDeadline())), OrganizeFailureCode.NOT_STARTED);
        failAll(repository.findLost(now.minus(liveness.heartbeatTimeout())), OrganizeFailureCode.LOST);
        failAll(repository.findRunTooLong(now.minus(liveness.runLimit())), OrganizeFailureCode.TIMEOUT);
        expiry.sweep();
        // 결과 본문은 분석 완료부터 보관 기한까지만 둔다. 작업·실행 이력은 남긴다.
        repository.purgeExpiredResults(now);
        repository.purgeIdempotencyBefore(now.minus(properties.idempotencyRetention()));
    }

    private void failAll(List<OrganizeExecution> executions, OrganizeFailureCode code) {
        for (OrganizeExecution execution : executions) {
            if (transitions.fail(execution.jobId(), execution.id(), code, null)) {
                log.warn("사진 정리 작업을 실패 처리했습니다. executionId={}, code={}", execution.executionId(), code);
            }
        }
    }
}
