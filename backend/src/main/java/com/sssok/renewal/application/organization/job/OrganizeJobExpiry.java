package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// 검토 기한(분석 완료 +30분)이 지난 READY 를 EXPIRED 로 닫는다. 스위퍼가 늦어도 읽고 쓰는 쪽에서 직접 판단하므로
// 만료된 작업을 조회하거나 반영하거나 새 접수를 막는 데 쓰지 못한다.
@Component
@RequiredArgsConstructor
public class OrganizeJobExpiry {
    private static final int SWEEP_BATCH = 100;

    private final OrganizeJobRepository repository;
    private final OrganizeJobTransitions transitions;
    @Qualifier("organizeClock")
    private final Clock clock;

    // 기한이 지났으면 만료 처리하고 최신 작업을 돌려준다. 다른 요청이 먼저 처리했어도 최신 상태를 읽는다.
    public OrganizeJob refresh(OrganizeJob job) {
        if (!job.isReviewOverdue(clock.instant())) {
            return job;
        }
        transitions.expire(job.id());
        return repository.findById(job.id()).orElse(job);
    }

    // 새 접수는 방을 점유한 READY 의 기한이 지났다면 먼저 정리한 뒤 활성 제한을 검사한다.
    public void expireActiveOfRoom(Long roomId) {
        repository.findActiveByRoom(roomId).ifPresent(this::refresh);
    }

    public int sweep() {
        Instant now = clock.instant();
        List<Long> due = repository.findReviewOverdueIds(now, SWEEP_BATCH);
        int expired = 0;
        for (Long jobId : due) {
            if (transitions.expire(jobId)) {
                expired++;
            }
        }
        return expired;
    }
}
