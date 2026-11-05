package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 진행 중·검토 중 작업을 취소한다. READY 취소는 검토 포기(닫기)를 겸한다.
// Python 에는 취소 기능이 없어서 분석은 계속될 수 있다. 서버는 결과를 버리고 이후 이벤트를 폐기·확인한다.
@Service
@RequiredArgsConstructor
public class OrganizeCancelService {
    private final OrganizeJobQueryService queryService;
    private final OrganizeJobRepository repository;
    private final OrganizeChangeNotifier notifier;
    @Qualifier("organizeClock")
    private final Clock clock;

    // 이미 FAILED·CANCELED·EXPIRED 면 그대로 돌려주고(멱등), APPLIED 는 거절한다.
    // 기한이 지난 READY 는 읽는 순간 EXPIRED 가 되므로 취소하지 않고 만료 상태로 돌려준다.
    // 읽은 뒤 다른 요청이 먼저 상태를 바꾸면 조건부 UPDATE 가 0 건이라, 최신 상태를 다시 읽어 판단한다.
    @Transactional
    public OrganizeJob cancel(Long roomId, Long memberId, UUID jobId) {
        OrganizeJob job = queryService.get(roomId, memberId, jobId);
        if (!job.status().isCancelable()) {
            return requireNotApplied(job);
        }
        Instant now = clock.instant();
        Optional<OrganizeExecution> latest = repository.findLatestExecution(job.id());
        if (!repository.markJobCanceled(job.id(), now)) {
            return requireNotApplied(repository.findById(job.id()).orElseThrow());
        }
        latest.ifPresent(execution -> repository.markExecutionFinished(
            execution.id(), OrganizeExecutionStatus.CANCELED, now));
        // 분석 서버가 받기 전에 닫은 작업은 체험을 쓰지 않은 것이다. 받았을 수 있으면 사용한 것으로 둔다.
        if (latest.isPresent() && !latest.get().mayHaveReachedAnalysis()) {
            repository.releaseTrial(job.id());
        }
        notifier.changed(job.id(), job.roomId(), Occupancy.FREE);
        return repository.findById(job.id()).orElseThrow();
    }

    private OrganizeJob requireNotApplied(OrganizeJob job) {
        if (job.status() == OrganizeJobStatus.APPLIED) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_JOB_NOT_CANCELABLE);
        }
        return job;
    }
}
