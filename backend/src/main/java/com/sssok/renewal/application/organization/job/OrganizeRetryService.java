package com.sssok.renewal.application.organization.job;

import com.sssok.application.port.out.FileRepository;
import com.sssok.common.exception.ErrorCode;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy.Mode;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeTargetPhoto;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 실패한 작업을 같은 jobId 로 다시 시도한다. 의미 있는 일시적 실패만 허용한다.
// - 전달만 실패한 작업은 같은 실행 ID 로 다시 보낸다(새 분석을 만들지 않는다).
// - 분석 서버가 실패로 끝냈다고 알려 온 작업은 새 실행 회차를 만든다(작업당 최대 횟수 제한).
// - 원격 실행이 끝났는지 모르는 실패(NOT_STARTED·LOST·TIMEOUT)는 새 실행을 만들지 않는다.
@Service
public class OrganizeRetryService {
    private final OrganizeJobProperties properties;
    private final OrganizeJobQueryService queryService;
    private final OrganizeIdempotency idempotency;
    private final OrganizeRetryPolicy retryPolicy;
    private final OrganizeMemberPolicy memberPolicy;
    private final OrganizeJobRepository repository;
    private final OrganizeChangeNotifier notifier;
    private final FileRepository fileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public OrganizeRetryService(OrganizeJobProperties properties, OrganizeJobQueryService queryService,
                                OrganizeIdempotency idempotency, OrganizeRetryPolicy retryPolicy,
                                OrganizeMemberPolicy memberPolicy, OrganizeJobRepository repository,
                                OrganizeChangeNotifier notifier, FileRepository fileRepository,
                                ApplicationEventPublisher eventPublisher,
                                @Qualifier("organizeClock") Clock clock, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.queryService = queryService;
        this.idempotency = idempotency;
        this.retryPolicy = retryPolicy;
        this.memberPolicy = memberPolicy;
        this.repository = repository;
        this.notifier = notifier;
        this.fileRepository = fileRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // started=false 는 이미 진행 중이거나 검토 중이라 새 실행 없이 최신 작업만 돌려준 경우다(200).
    public record Result(OrganizeJob job, boolean started) {
    }

    public Result retry(Long roomId, Long memberId, UUID jobId, String idempotencyKey) {
        if (!properties.enabled()) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_UNAVAILABLE);
        }
        String key = idempotency.requireKey(idempotencyKey);
        String hash = OrganizeIdempotency.hash("RETRY:" + jobId);
        OrganizeJob job = queryService.get(roomId, memberId, jobId);
        Optional<OrganizeIdempotency.Replay> replay =
            idempotency.find(memberId, roomId, OrganizeIdempotency.RETRY, key, hash);
        if (replay.isPresent()) {
            return new Result(job, true);
        }
        if (job.status().holdsRoom()) {
            return new Result(job, false);
        }
        Mode mode = retryPolicy.modeOf(job)
            .orElseThrow(() -> new OrganizeJobException(ErrorCode.ORGANIZE_RETRY_NOT_ALLOWED));
        requireRetryDelayPassed(job);
        requireTargetsUnchanged(job);
        try {
            transaction.executeWithoutResult(status -> restart(job, mode, key, hash));
        } catch (DataIntegrityViolationException conflict) {
            // 같은 키의 동시 재전송이거나, 그 사이 다른 작업이 방을 차지한 경우다.
            if (idempotency.find(memberId, roomId, OrganizeIdempotency.RETRY, key, hash).isPresent()) {
                return new Result(queryService.get(roomId, memberId, jobId), true);
            }
            Optional<OrganizeJob> active = repository.findActiveByRoom(roomId);
            if (active.isEmpty()) {
                throw conflict;
            }
            throw OrganizeJobInProgressException.of(active.get(), memberId);
        }
        return new Result(queryService.get(roomId, memberId, jobId), true);
    }

    private void requireRetryDelayPassed(OrganizeJob job) {
        Instant availableAt = retryPolicy.retryAvailableAt(job);
        Instant now = clock.instant();
        if (availableAt != null && availableAt.isAfter(now)) {
            throw new OrganizeRateLimitedException(Duration.between(now, availableAt).toSeconds() + 1);
        }
    }

    // 원래 대상 사진은 그대로 쓴다. 그 사이 삭제되거나 보이지 않게 된 사진이 있으면 새 작업을 접수해야 한다.
    private void requireTargetsUnchanged(OrganizeJob job) {
        List<Long> mediaIds = repository.findPhotos(job.id()).stream().map(OrganizeTargetPhoto::mediaId).toList();
        Set<Long> visible = fileRepository.findAllByRoomIdAndIdIn(job.roomId(), mediaIds).stream()
            .filter(file -> UploadStatus.visibleStatuses().contains(file.getStatus()))
            .map(StoredFile::getId)
            .collect(Collectors.toSet());
        if (visible.size() != mediaIds.size()) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_TARGET_CHANGED);
        }
    }

    private void restart(OrganizeJob job, Mode mode, String key, String hash) {
        Instant now = clock.instant();
        Instant deadline = now.plus(properties.dispatch().deadline());
        // 먼저 작업을 QUEUED 로 돌려 방을 다시 점유한다. 다른 작업이 방을 쓰고 있으면 유니크 제약이 거절한다.
        if (!repository.markJobRetried(job.id(), mode == Mode.REANALYZE ? 1 : 0, now)) {
            throw new OrganizeJobException(ErrorCode.ORGANIZE_RETRY_NOT_ALLOWED);
        }
        memberPolicy.reacquireTrial(job.requesterId(), job.roomId(), job.id(), now);
        OrganizeExecution latest = repository.findLatestExecution(job.id()).orElseThrow();
        OrganizeExecution next;
        if (mode == Mode.RESEND) {
            if (!repository.rescheduleExecution(latest.id(), now, deadline)) {
                throw new OrganizeJobException(ErrorCode.ORGANIZE_RETRY_NOT_ALLOWED);
            }
            next = latest;
        } else {
            next = repository.addExecution(job.id(), latest.attempt() + 1, UUID.randomUUID(), now, deadline);
        }
        idempotency.record(job.requesterId(), job.roomId(), OrganizeIdempotency.RETRY, key, hash, job.id(), null, now);
        notifier.changed(job.id(), job.roomId(), Occupancy.BUSY);
        eventPublisher.publishEvent(new OrganizeJobCreatedEvent(next.id()));
    }
}
