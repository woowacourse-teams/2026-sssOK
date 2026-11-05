package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
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
}
