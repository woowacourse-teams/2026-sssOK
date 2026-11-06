package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 분석 서버가 보낸 이벤트를 상태로 옮기는 규칙을 확인한다.
@OrganizeH2Test
class OrganizeEventServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeEventService eventService;
    @Autowired
    OrganizeCancelService cancelService;

    private Long host;
    private Long room;
    private List<Long> ids;
    private OrganizeJob job;

    @BeforeEach
    void setUp() {
        host = anonymousMember();
        room = room(host);
        ids = jpegs(room, host, 3);
        job = startPerson(room, host);
    }

    @Test
    void RUNNING은_진행을_저장하고_확인한다() {
        var event = new OrganizeEvent(job.publicId(), room,
            repository.findLatestExecution(job.id()).orElseThrow().executionId(), 1, "PERSON", "RUNNING", objectMapper.valueToTree(Map.of("stage", "detecting_faces",
                "counts", Map.of("processed", 1, "total", 3))), null, null);

        OrganizeEventAck ack = eventService.handle(event);

        assertThat(ack.accepted()).isTrue();
        OrganizeJob running = reload(job);
        assertThat(running.status()).isEqualTo(OrganizeJobStatus.RUNNING);
        assertThat(running.progressStage()).isEqualTo("detecting_faces");
        assertThat(running.progressProcessed()).isEqualTo(1);
        assertThat(running.progressTotal()).isEqualTo(3);
        assertThat(running.jobVersion()).isGreaterThan(job.jobVersion());
        assertThat(repository.findLatestExecution(job.id()).orElseThrow().status())
            .isEqualTo(OrganizeExecutionStatus.RUNNING);
    }

    @Test
    void 완료하면_READY이고_검토와_보관_기한이_분석_완료_시각부터_따로_정해진다() {
        eventService.handle(completed(job, personResult(ids.subList(0, 2), List.of(ids.get(2)))));

        OrganizeJob ready = reload(job);
        assertThat(ready.status()).isEqualTo(OrganizeJobStatus.READY);
        assertThat(ready.resultVersion()).isEqualTo(1);
        assertThat(ready.hasResult()).isTrue();
        assertThat(Duration.between(clock.instant(), ready.reviewExpiresAt())).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
        assertThat(Duration.between(clock.instant(), ready.resultExpiresAt())).isBetween(Duration.ofDays(3).minusMinutes(1), Duration.ofDays(3));
        assertThat(repository.findLatestExecution(job.id()).orElseThrow().status())
            .isEqualTo(OrganizeExecutionStatus.COMPLETED);
    }

    @Test
    void READY는_방을_계속_점유한다() {
        eventService.handle(completed(job, personResult(ids, List.of())));

        assertThat(repository.findActiveByRoom(room)).get().extracting(OrganizeJob::publicId).isEqualTo(job.publicId());
        assertThat(repository.findRoomState(room)).get().extracting("busy", "version").containsExactly(true, 1L);
    }

    @Test
    void 그룹이_0개여도_성공이다() {
        var empty = objectMapper.valueToTree(Map.of("groups", List.of(), "unassignedMediaIds", ids, "errors", List.of(),
            "counts", Map.of(), "pipelineVersion", "v1"));

        eventService.handle(completed(job, empty));

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.READY);
    }

    @Test
    void 같은_완료를_다시_받으면_확인만_하고_저장하지_않는다() {
        var event = completed(job, personResult(ids, List.of()));
        eventService.handle(event);
        OrganizeJob first = reload(job);

        OrganizeEventAck ack = eventService.handle(event);

        assertThat(ack.accepted()).isTrue();
        assertThat(reload(job).jobVersion()).isEqualTo(first.jobVersion());
    }

    @Test
    void 완료_뒤에_RUNNING이_와도_상태가_되돌아가지_않는다() {
        eventService.handle(completed(job, personResult(ids, List.of())));

        eventService.handle(running(job));

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.READY);
    }

    @Test
    void 대상이_아닌_사진이_있는_결과는_RESULT_INVALID로_실패하고_방을_푼다() {
        eventService.handle(completed(job, personResult(List.of(ids.get(0), 999_999L), List.of())));

        OrganizeJob failed = reload(job);
        assertThat(failed.status()).isEqualTo(OrganizeJobStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("RESULT_INVALID");
        assertThat(repository.findActiveByRoom(room)).isEmpty();
        assertThat(repository.findRoomState(room)).get().extracting("busy", "version").containsExactly(false, 2L);
    }

    @Test
    void FAILED는_분석_서버의_오류_코드를_남기고_익명_체험을_돌려준다() {
        assertThat(repository.hasTrial(host, room)).isTrue();

        eventService.handle(failed(job, "SERVER_RESTARTED"));

        OrganizeJob failed = reload(job);
        assertThat(failed.status()).isEqualTo(OrganizeJobStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("ANALYSIS_FAILED");
        assertThat(failed.failureDetail()).isEqualTo("SERVER_RESTARTED");
        assertThat(failed.finishedAt()).isNotNull();
        assertThat(repository.hasTrial(host, room)).isFalse();
    }

    @Test
    void 모르는_실행_다른_작업_종류_다른_회차의_이벤트는_저장하지_않고_확인한다() {
        var unknown = new OrganizeEvent(job.publicId(), room, UUID.randomUUID(), 1, "PERSON", "FAILED",
            objectMapper.createObjectNode(), null, "X");
        var otherKind = new OrganizeEvent(job.publicId(), room, repository.findLatestExecution(job.id()).orElseThrow()
            .executionId(), 1, "SIMILAR", "FAILED", objectMapper.createObjectNode(), null, "X");
        var otherAttempt = new OrganizeEvent(job.publicId(), room, repository.findLatestExecution(job.id()).orElseThrow()
            .executionId(), 2, "PERSON", "FAILED", objectMapper.createObjectNode(), null, "X");

        assertThat(eventService.handle(unknown).accepted()).isTrue();
        assertThat(eventService.handle(otherKind).accepted()).isTrue();
        assertThat(eventService.handle(otherAttempt).accepted()).isTrue();

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.QUEUED);
    }

    @Test
    void 취소된_작업의_늦은_결과는_버리고_확인한다() {
        states.accept(job);
        cancelService.cancel(room, host, job.publicId());

        OrganizeEventAck ack = eventService.handle(completed(job, personResult(ids, List.of())));

        assertThat(ack.accepted()).isTrue();
        OrganizeJob canceled = reload(job);
        assertThat(canceled.status()).isEqualTo(OrganizeJobStatus.CANCELED);
        assertThat(canceled.hasResult()).isFalse();
    }

    @Test
    void MCP가_접수하면_RUNNING이고_단계는_waiting이다() {
        states.accept(job);

        OrganizeJob accepted = reload(job);
        assertThat(accepted.status()).isEqualTo(OrganizeJobStatus.RUNNING);
        assertThat(accepted.progressStage()).isEqualTo("waiting");
        assertThat(accepted.progressProcessed()).isNull();
        assertThat(accepted.progressTotal()).isNull();
        assertThat(accepted.progressUnit()).isNull();
    }

    @Test
    void 진행_이벤트가_먼저_오면_늦은_접수_응답이_그_진행을_덮어쓰지_않는다() {
        eventService.handle(progress(job, "downloading_photos", 3, 10, "PHOTO"));
        OrganizeJob running = reload(job);

        states.accept(job);

        OrganizeJob afterAccepted = reload(job);
        assertThat(afterAccepted.progressStage()).isEqualTo("downloading_photos");
        assertThat(afterAccepted.progressProcessed()).isEqualTo(3);
        assertThat(afterAccepted.progressUnit()).isEqualTo("PHOTO");
        assertThat(afterAccepted.jobVersion()).isEqualTo(running.jobVersion());
    }

    @Test
    void 같은_진행을_다시_받으면_버전은_그대로이고_생존_시각만_갱신된다() {
        states.accept(job);
        eventService.handle(progress(job, "detecting_faces", 24, 60, "PHOTO"));
        OrganizeJob first = reload(job);
        Instant firstEventAt = repository.findLatestExecution(job.id()).orElseThrow().lastEventAt();

        clock.advance(Duration.ofSeconds(15));
        eventService.handle(progress(job, "detecting_faces", 24, 60, "PHOTO"));

        assertThat(reload(job).jobVersion()).isEqualTo(first.jobVersion());
        assertThat(repository.findLatestExecution(job.id()).orElseThrow().lastEventAt()).isAfter(firstEventAt);
    }

    @Test
    void 처리_수와_단위가_바뀌면_저장하고_버전이_오른다() {
        states.accept(job);
        eventService.handle(progress(job, "extracting_body_features", 2, 8, "BODY"));

        OrganizeJob running = reload(job);
        assertThat(running.progressStage()).isEqualTo("extracting_body_features");
        assertThat(running.progressProcessed()).isEqualTo(2);
        assertThat(running.progressTotal()).isEqualTo(8);
        assertThat(running.progressUnit()).isEqualTo("BODY");

        eventService.handle(progress(job, "extracting_body_features", 3, 8, "BODY"));

        assertThat(reload(job).jobVersion()).isEqualTo(running.jobVersion() + 1);
    }

    @Test
    void 같은_회차의_지연된_이벤트는_진행과_버전을_되돌리지_않는다() {
        states.accept(job);
        eventService.handle(progress(job, "detecting_faces", 24, 60, "PHOTO"));
        OrganizeJob current = reload(job);

        eventService.handle(progress(job, "preparing", 60, 60, "PHOTO"));
        eventService.handle(progress(job, "detecting_faces", 10, 60, "PHOTO"));

        OrganizeJob after = reload(job);
        assertThat(after.progressStage()).isEqualTo("detecting_faces");
        assertThat(after.progressProcessed()).isEqualTo(24);
        assertThat(after.jobVersion()).isEqualTo(current.jobVersion());
    }

    @Test
    void 완료하면_READY에서_마지막_중간_진행이_모두_비워진다() {
        states.accept(job);
        eventService.handle(progress(job, "extracting_body_features", 2, 8, "BODY"));

        eventService.handle(completed(job, personResult(ids, List.of())));

        OrganizeJob ready = reload(job);
        assertThat(ready.status()).isEqualTo(OrganizeJobStatus.READY);
        assertThat(ready.progressStage()).isNull();
        assertThat(ready.progressProcessed()).isNull();
        assertThat(ready.progressTotal()).isNull();
        assertThat(ready.progressUnit()).isNull();
    }

    private OrganizeEvent progress(OrganizeJob target, String stage, int processed, int total, String unit) {
        var progress = objectMapper.createObjectNode().put("stage", stage);
        progress.put("processed", processed).put("total", total).put("unit", unit);
        var execution = repository.findLatestExecution(target.id()).orElseThrow();
        return new OrganizeEvent(target.publicId(), target.roomId(), execution.executionId(), execution.attempt(),
            target.kind().name(), "RUNNING", progress, null, null);
    }
}
