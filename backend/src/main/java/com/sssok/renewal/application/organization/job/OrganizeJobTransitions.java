package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 작업과 실행의 상태를 같은 트랜잭션에서 함께 바꾼다. 모든 변경은 조건부 UPDATE 라서 이미 종료된 작업은 건드리지 않는다.
// 바뀐 작업은 커밋 뒤 실시간 알림으로 나간다. 방을 놓는 전이(실패·만료)에서는 방 점유 버전도 올린다.
@Component
@RequiredArgsConstructor
public class OrganizeJobTransitions {
    private final OrganizeJobRepository repository;
    private final OrganizeChangeNotifier notifier;
    @Qualifier("organizeClock")
    private final Clock clock;

    @Transactional
    public void accepted(Long jobId, Long executionPk) {
        Instant now = clock.instant();
        repository.markExecutionAccepted(executionPk, now);
        if (repository.markJobRunning(jobId, now)) {
            notify(jobId, Occupancy.UNCHANGED);
        }
    }

    // 최종 실패는 익명 회원의 체험 1회를 돌려준다. 서버나 분석 쪽 사유로 끝난 작업이 체험을 쓰게 두지 않는다.
    @Transactional
    public boolean fail(Long jobId, Long executionPk, OrganizeFailureCode code, String detail) {
        Instant now = clock.instant();
        boolean failed = repository.markJobFailed(jobId, code, detail, now);
        repository.markExecutionFinished(executionPk, OrganizeExecutionStatus.FAILED, now);
        if (failed) {
            repository.releaseTrial(jobId);
            notify(jobId, Occupancy.FREE);
        }
        return failed;
    }

    @Transactional
    public void discard(Long executionPk) {
        repository.markExecutionFinished(executionPk, OrganizeExecutionStatus.CANCELED, clock.instant());
    }

    // 검토 기한이 지난 READY 를 EXPIRED 로 닫고 방을 놓는다. 반영·취소와 겹치면 조건부 UPDATE 로 한쪽만 성공한다.
    @Transactional
    public boolean expire(Long jobId) {
        boolean expired = repository.markJobExpired(jobId, clock.instant());
        if (expired) {
            notify(jobId, Occupancy.FREE);
        }
        return expired;
    }

    private void notify(Long jobId, Occupancy occupancy) {
        repository.findById(jobId).ifPresent(job -> notifier.changed(job.id(), job.roomId(), occupancy));
    }
}
