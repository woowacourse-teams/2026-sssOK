package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobDraft;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizePhotoSource;
import com.sssok.renewal.domain.organization.OrganizeTargetPhoto;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class OrganizeJobRepository {
    private static final int MAX_DETAIL_LENGTH = 500;

    private final OrganizeJobJpaRepository jobs;
    private final OrganizeExecutionJpaRepository executions;
    private final OrganizeJobPhotoJpaRepository photos;
    private final OrganizeTrialJpaRepository trials;
    private final OrganizeStartGateJpaRepository gates;
    private final OrganizeIdempotencyJpaRepository idempotencies;
    private final OrganizeRoomStateJpaRepository roomStates;

    public record RoomState(long version, boolean busy) {
    }

    public record IdempotencyRecord(String requestHash, Long jobId, String response) {
    }

    // 작업·실행·대상 사진을 같은 트랜잭션에 저장한다. active_room_id 유니크 위반은 flush 에서 바로 드러난다.
    public OrganizeJob create(OrganizeJobDraft draft, UUID executionId, List<OrganizeTargetPhoto> targets,
                              Instant now, Instant dispatchDeadline) {
        OrganizeJobJpaEntity job = jobs.saveAndFlush(new OrganizeJobJpaEntity(draft.publicId(), draft.roomId(),
            draft.requesterId(), draft.kind().name(), draft.trigger().name(), OrganizeJobStatus.QUEUED.name(),
            draft.requestText(), draft.serverId(), draft.toolName(), draft.toolDescription(), draft.photoCount(),
            draft.excludedCount(), draft.roomId(), now));
        executions.save(new OrganizeExecutionJpaEntity(job.getId(), executionId, 1,
            OrganizeExecutionStatus.DISPATCH_PENDING.name(), now, dispatchDeadline, now));
        photos.saveAll(targets.stream()
            .map(photo -> new OrganizeJobPhotoJpaEntity(job.getId(), photo.mediaId(), photo.extension(),
                photo.source().name(), now))
            .toList());
        return toJob(job);
    }

    public Optional<OrganizeJob> findById(Long id) {
        return jobs.findById(id).map(this::toJob);
    }

    public Optional<OrganizeJob> findByPublicId(UUID publicId) {
        return jobs.findByPublicId(publicId).map(this::toJob);
    }

    public Optional<OrganizeJob> findActiveByRoom(Long roomId) {
        return jobs.findByActiveRoomId(roomId).map(this::toJob);
    }

    // 요청자의 가장 최근 종료 작업. finishedAfter 이후에 끝난 것만 본다.
    public Optional<OrganizeJob> findLatestTerminalOf(Long roomId, Long requesterId, Instant finishedAfter) {
        return jobs.findFirstByRoomIdAndRequesterIdAndStatusInAndFinishedAtAfterOrderByFinishedAtDescIdDesc(roomId,
            requesterId, OrganizeJobStatus.terminal().stream().map(OrganizeJobStatus::name).toList(), finishedAfter)
            .map(this::toJob);
    }

    public List<Long> findReviewOverdueIds(Instant now, int limit) {
        return jobs.findReviewOverdueIds(now, PageRequest.of(0, limit));
    }

    public Optional<OrganizeExecution> findExecutionByExecutionId(UUID executionId) {
        return executions.findByExecutionId(executionId).map(this::toExecution);
    }

    public Optional<OrganizeExecution> findExecutionById(Long id) {
        return executions.findById(id).map(this::toExecution);
    }

    public Optional<OrganizeExecution> findLatestExecution(Long jobId) {
        return executions.findFirstByJobIdOrderByAttemptDesc(jobId).map(this::toExecution);
    }

    public List<OrganizeTargetPhoto> findPhotos(Long jobId) {
        return photos.findAllByJobIdOrderByMediaId(jobId).stream()
            .map(photo -> new OrganizeTargetPhoto(photo.getMediaId(), photo.getExtension(),
                OrganizePhotoSource.valueOf(photo.getSource())))
            .toList();
    }

    public boolean markJobRunning(Long jobId, Instant now) {
        return jobs.markRunning(jobId, now) == 1;
    }

    // 읽은 jobVersion 이 그대로일 때만 쓴다. 그 사이 다른 이벤트·접수 응답이 먼저 바꿨다면 0 행이라 덮어쓰지 않는다.
    public boolean updateProgress(Long jobId, int expectedVersion, String stage, Integer processed, Integer total,
                                  String unit, Instant now) {
        return jobs.updateProgress(jobId, expectedVersion, stage, processed, total, unit, now) == 1;
    }

    public boolean markJobReady(Long jobId, int resultVersion, String result, Instant reviewExpiresAt,
                                Instant resultExpiresAt, Instant now) {
        return jobs.markReady(jobId, resultVersion, result, reviewExpiresAt, resultExpiresAt, now) == 1;
    }

    public boolean markJobFailed(Long jobId, OrganizeFailureCode code, String detail, Instant now) {
        return jobs.markFailed(jobId, code.name(), truncate(detail), now) == 1;
    }

    public boolean markJobCanceled(Long jobId, Instant now) {
        return jobs.markCanceled(jobId, now) == 1;
    }

    public boolean markJobExpired(Long jobId, Instant now) {
        return jobs.markExpired(jobId, now) == 1;
    }

    public boolean markJobApplied(Long jobId, Instant now) {
        return jobs.markApplied(jobId, now) == 1;
    }

    // FAILED → QUEUED. 새 실행 회차를 만드는 재시도면 retryIncrement 를 1로 준다.
    public boolean markJobRetried(Long jobId, int retryIncrement, Instant now) {
        return jobs.markRetried(jobId, retryIncrement, now) == 1;
    }

    public int purgeExpiredResults(Instant now) {
        return jobs.purgeExpiredResults(now);
    }

    public OrganizeExecution addExecution(Long jobId, int attempt, UUID executionId, Instant now,
                                          Instant dispatchDeadline) {
        return toExecution(executions.save(new OrganizeExecutionJpaEntity(jobId, executionId, attempt,
            OrganizeExecutionStatus.DISPATCH_PENDING.name(), now, dispatchDeadline, now)));
    }

    public boolean rescheduleExecution(Long executionPk, Instant now, Instant dispatchDeadline) {
        return executions.reschedule(executionPk, now, dispatchDeadline) == 1;
    }

    // ---- 익명 체험 ----

    public boolean hasTrial(Long memberId, Long roomId) {
        return trials.existsByMemberIdAndRoomId(memberId, roomId);
    }

    // (회원, 방) 유니크라서 이미 쓴 체험이면 DataIntegrityViolationException 이 난다.
    public void reserveTrial(Long memberId, Long roomId, Long jobId, Instant now) {
        trials.saveAndFlush(new OrganizeTrialJpaEntity(memberId, roomId, jobId, now));
    }

    public boolean releaseTrial(Long jobId) {
        return trials.deleteByJobId(jobId) > 0;
    }

    // ---- 접수 간격(OAuth) ----

    // 마지막 접수가 interval 이전일 때만 지금을 기록하고 true 를 돌려준다. 첫 접수는 행을 만든다.
    public boolean tryPassStartGate(Long memberId, Instant now, Duration interval) {
        if (gates.touchIfIdle(memberId, now, now.minus(interval)) == 1) {
            return true;
        }
        if (gates.existsById(memberId)) {
            return false;
        }
        gates.saveAndFlush(new OrganizeStartGateJpaEntity(memberId, now, now));
        return true;
    }

    public Optional<Instant> lastStartedAt(Long memberId) {
        return gates.findById(memberId).map(OrganizeStartGateJpaEntity::getLastStartedAt);
    }

    // ---- 멱등 기록 ----

    public Optional<IdempotencyRecord> findIdempotency(Long memberId, Long roomId, String operation, String key) {
        return idempotencies.findByMemberIdAndRoomIdAndOperationAndIdemKey(memberId, roomId, operation, key)
            .map(found -> new IdempotencyRecord(found.getRequestHash(), found.getJobId(), found.getResponse()));
    }

    // (회원, 방, 연산, 키) 유니크라서 같은 키가 동시에 오면 한쪽만 저장된다.
    public void saveIdempotency(Long memberId, Long roomId, String operation, String key, String requestHash,
                                Long jobId, String response, Instant now) {
        idempotencies.saveAndFlush(new OrganizeIdempotencyJpaEntity(memberId, roomId, operation, key, requestHash,
            jobId, response, now));
    }

    public int purgeIdempotencyBefore(Instant threshold) {
        return idempotencies.deleteCreatedBefore(threshold);
    }

    // ---- 방 점유 상태 ----

    public Optional<RoomState> findRoomState(Long roomId) {
        return roomStates.findById(roomId).map(state -> new RoomState(state.getVersion(), state.isBusy()));
    }

    // 점유가 바뀔 때 버전을 올리고 새 상태를 돌려준다. 행이 없으면 만든다.
    public RoomState bumpRoomState(Long roomId, boolean busy, Instant now) {
        if (roomStates.bump(roomId, busy, now) == 0) {
            roomStates.saveAndFlush(new OrganizeRoomStateJpaEntity(roomId, 1L, busy, now));
        }
        return findRoomState(roomId).orElseThrow();
    }

    public List<Long> findDispatchableExecutionIds(Instant now, int limit) {
        return executions.findDispatchableIds(now, PageRequest.of(0, limit));
    }

    public boolean claimExecution(Long executionPk, Instant now, Instant leaseUntil) {
        return executions.claim(executionPk, now, leaseUntil) == 1;
    }

    public boolean markExecutionAccepted(Long executionPk, Instant now) {
        return executions.markAccepted(executionPk, now) == 1;
    }

    public boolean scheduleDispatchRetry(Long executionPk, OrganizeExecutionStatus status, Instant next, Instant now) {
        return executions.scheduleRetry(executionPk, status.name(), next, now) == 1;
    }

    public boolean markExecutionRunning(Long executionPk, Instant now) {
        return executions.markRunning(executionPk, now) == 1;
    }

    public boolean markExecutionFinished(Long executionPk, OrganizeExecutionStatus status, Instant now) {
        return executions.markFinished(executionPk, status.name(), now) == 1;
    }

    public List<OrganizeExecution> findDispatchExpired(Instant now) {
        return executions.findDispatchExpired(now).stream().map(this::toExecution).toList();
    }

    public List<OrganizeExecution> findNotStarted(Instant threshold) {
        return executions.findNotStarted(threshold).stream().map(this::toExecution).toList();
    }

    public List<OrganizeExecution> findLost(Instant threshold) {
        return executions.findLost(threshold).stream().map(this::toExecution).toList();
    }

    public List<OrganizeExecution> findRunTooLong(Instant threshold) {
        return executions.findRunTooLong(threshold).stream().map(this::toExecution).toList();
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }

    private OrganizeJob toJob(OrganizeJobJpaEntity entity) {
        return new OrganizeJob(entity.getId(), entity.getPublicId(), entity.getRoomId(), entity.getRequesterId(),
            OrganizeKind.valueOf(entity.getKind()), OrganizeTrigger.valueOf(entity.getTriggerType()),
            OrganizeJobStatus.valueOf(entity.getStatus()), entity.getRequestText(), entity.getServerId(),
            entity.getToolName(), entity.getToolDescription(), entity.getPhotoCount(), entity.getExcludedCount(),
            entity.getJobVersion(), entity.getRetryCount(), entity.getFailureCode(), entity.getFailureDetail(),
            entity.getResultVersion(), entity.getResult(), entity.getProgressStage(), entity.getProgressProcessed(),
            entity.getProgressTotal(), entity.getProgressUnit(), entity.getReviewExpiresAt(), entity.getResultExpiresAt(),
            entity.getFinishedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private OrganizeExecution toExecution(OrganizeExecutionJpaEntity entity) {
        return new OrganizeExecution(entity.getId(), entity.getJobId(), entity.getExecutionId(),
            entity.getAttempt(), OrganizeExecutionStatus.valueOf(entity.getStatus()), entity.getDispatchAttempts(),
            entity.getDispatchDeadlineAt(), entity.getAcceptedAt(), entity.getStartedAt(), entity.getLastEventAt(),
            entity.getFinishedAt());
    }
}
