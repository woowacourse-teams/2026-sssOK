package com.sssok.renewal.incoming.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.renewal.application.organization.job.OrganizeJobChangedEvent;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 커밋된 작업 변화를 기존 방 SSE 로 내보낸다. 방 전체에는 busy 와 방 버전만, 작업 상세는 요청자에게만 보낸다.
// room_events 를 저장해야 해서 새 트랜잭션을 쓴다(기존 방 이벤트 리스너와 같은 방식).
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizeJobEventListener {
    static final String ROOM_CHANGED = "organize.room.changed";
    static final String JOB_UPDATED = "organize.job.updated";

    private final EventPublisherPort eventPublisher;
    private final OrganizeJobRepository repository;
    private final OrganizeRetryPolicy retryPolicy;

    public record RoomChanged(Long roomId, Long roomVersion, boolean busy) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobUpdated(UUID jobId, int jobVersion, int attempt, String status,
                             OrganizeJobResponse.Progress progress, String failureCode, boolean retryable,
                             @JsonInclude(JsonInclude.Include.ALWAYS) Instant retryAvailableAt,
                             @JsonInclude(JsonInclude.Include.ALWAYS) Instant reviewExpiresAt,
                             @JsonInclude(JsonInclude.Include.ALWAYS) Instant resultExpiresAt) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrganizeJobChangedEvent event) {
        try {
            if (event.roomVersion() != null) {
                eventPublisher.publish(event.roomId(), ROOM_CHANGED,
                    new RoomChanged(event.roomId(), event.roomVersion(), event.roomBusy()));
            }
            repository.findById(event.jobPk()).ifPresent(job ->
                eventPublisher.publish(event.roomId(), JOB_UPDATED, payload(job), job.requesterId()));
        } catch (RuntimeException failure) {
            // 알림이 실패해도 이미 커밋된 작업은 되돌리지 않는다. 화면은 재연결 때 스냅샷으로 맞춘다.
            log.warn("사진 정리 실시간 알림을 보내지 못했습니다. jobPk={}", event.jobPk(), failure);
        }
    }

    private JobUpdated payload(OrganizeJob job) {
        // 새 실행 회차는 사용자 재시도로만 생기므로 회차는 재시도 횟수에서 바로 나온다.
        int attempt = job.retryCount() + 1;
        OrganizeJobResponse.Progress progress = OrganizeJobResponse.progressOf(job);
        return new JobUpdated(job.publicId(), job.jobVersion(), attempt, job.status().name(), progress,
            job.failureCode(), retryPolicy.retryable(job), retryPolicy.retryAvailableAt(job),
            job.reviewExpiresAt(), job.resultExpiresAt());
    }
}
