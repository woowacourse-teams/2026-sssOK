package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobDraft;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizePhotoSource;
import com.sssok.renewal.domain.organization.OrganizeTargetPhoto;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 작업·실행·대상 사진·회원 사용권·멱등 기록을 한 트랜잭션으로 저장하고, 커밋되면 발송을 시작하게 한다.
// 방에 활성 작업이 이미 있으면 DB 유니크 제약(active_room_id)이 DataIntegrityViolationException 으로 거절한다.
// 사용권과 접수 간격은 작업을 저장한 뒤 같은 트랜잭션에서 확정해, 하나라도 실패하면 모두 되돌아간다.
@Component
@RequiredArgsConstructor
public class OrganizeJobCreator {
    private final OrganizeJobRepository repository;
    private final OrganizeJobProperties properties;
    private final OrganizeMemberPolicy memberPolicy;
    private final OrganizeIdempotency idempotency;
    private final OrganizeChangeNotifier notifier;
    private final ApplicationEventPublisher eventPublisher;
    @Qualifier("organizeClock")
    private final Clock clock;

    public record Request(Long roomId, Long memberId, OrganizeKind kind, OrganizeTrigger trigger, String text,
                          OrganizeTool tool, OrganizeTargetPhotoSelector.Selection selection,
                          String idempotencyKey, String requestHash) {
    }

    @Transactional
    public OrganizeJob create(Request request) {
        Instant now = clock.instant();
        OrganizePhotoSource source = properties.photoSourceOf(request.kind());
        List<OrganizeTargetPhoto> targets = request.selection().photos().stream()
            .map(photo -> new OrganizeTargetPhoto(photo.mediaId(), photo.extension(), source))
            .toList();
        OrganizeJobDraft draft = new OrganizeJobDraft(UUID.randomUUID(), request.roomId(), request.memberId(),
            request.kind(), request.trigger(), request.text(), request.tool().serverId(), request.tool().name(),
            request.tool().description(), targets.size(), request.selection().excludedCount());
        OrganizeJob job = repository.create(draft, UUID.randomUUID(), targets, now,
            now.plus(properties.dispatch().deadline()));
        memberPolicy.acquire(request.memberId(), request.roomId(), job.id(), now);
        idempotency.record(request.memberId(), request.roomId(), OrganizeIdempotency.START, request.idempotencyKey(),
            request.requestHash(), job.id(), null, now);
        notifier.changed(job.id(), job.roomId(), Occupancy.BUSY);
        OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();
        eventPublisher.publishEvent(new OrganizeJobCreatedEvent(execution.id()));
        return job;
    }
}
