package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 취소·검토 포기와 그때의 방 해제·체험 반환을 확인한다.
@OrganizeH2Test
class OrganizeCancelServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeCancelService cancelService;
    @Autowired
    OrganizeEventService eventService;

    private Long host;
    private Long room;
    private OrganizeJob job;

    @BeforeEach
    void setUp() {
        host = anonymousMember();
        room = room(host);
        jpegs(room, host, 2);
        job = startPerson(room, host);
    }

    @Test
    void 진행_중인_작업을_취소하면_방을_풀고_결과와_실행을_닫는다() {
        states.accept(job);

        OrganizeJob canceled = cancelService.cancel(room, host, job.publicId());

        assertThat(canceled.status()).isEqualTo(OrganizeJobStatus.CANCELED);
        assertThat(canceled.finishedAt()).isNotNull();
        assertThat(repository.findActiveByRoom(room)).isEmpty();
        assertThat(repository.findLatestExecution(job.id()).orElseThrow().status())
            .isEqualTo(OrganizeExecutionStatus.CANCELED);
        assertThat(repository.findRoomState(room)).get().extracting("busy", "version").containsExactly(false, 2L);
    }

    @Test
    void 검토_중인_작업도_취소할_수_있고_결과는_버린다() {
        states.ready(job);

        OrganizeJob canceled = cancelService.cancel(room, host, job.publicId());

        assertThat(canceled.status()).isEqualTo(OrganizeJobStatus.CANCELED);
        assertThat(canceled.hasResult()).isFalse();
        assertThat(repository.findActiveByRoom(room)).isEmpty();
    }

    @Test
    void 이미_취소된_작업을_다시_취소해도_그대로_돌려준다() {
        states.accept(job);
        OrganizeJob first = cancelService.cancel(room, host, job.publicId());

        OrganizeJob again = cancelService.cancel(room, host, job.publicId());

        assertThat(again.status()).isEqualTo(OrganizeJobStatus.CANCELED);
        assertThat(again.jobVersion()).isEqualTo(first.jobVersion());
    }

    @Test
    void 실패하거나_만료된_작업도_그대로_돌려준다() {
        states.analysisFailed(job);
        assertThat(cancelService.cancel(room, host, job.publicId()).status()).isEqualTo(OrganizeJobStatus.FAILED);

        Long other = registeredMember();
        Long otherRoom = room(other);
        jpegs(otherRoom, other, 2);
        OrganizeJob ready = startPerson(otherRoom, other);
        states.ready(ready);
        clock.advance(Duration.ofMinutes(31));
        assertThat(cancelService.cancel(otherRoom, other, ready.publicId()).status())
            .isEqualTo(OrganizeJobStatus.EXPIRED);
    }

    @Test
    void 반영된_작업은_취소할_수_없다() {
        states.ready(job);
        jdbc.update("UPDATE organize_job SET status = 'APPLIED', active_room_id = NULL WHERE id = ?", job.id());

        assertThatThrownBy(() -> cancelService.cancel(room, host, job.publicId()))
            .isInstanceOfSatisfying(SssOkException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_CANCELABLE));
    }

    @Test
    void 남의_작업이나_없는_작업은_404다() {
        Long guest = registeredMember();

        for (Runnable attempt : new Runnable[] {
            () -> cancelService.cancel(room, guest, job.publicId()),
            () -> cancelService.cancel(room, host, UUID.randomUUID()),
            () -> cancelService.cancel(room + 999, host, job.publicId())}) {
            assertThatThrownBy(attempt::run).isInstanceOfSatisfying(SssOkException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_FOUND));
        }
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.QUEUED);
    }

    @Test
    void 분석_서버에_닿기_전에_취소하면_익명_체험을_돌려준다() {
        assertThat(repository.hasTrial(host, room)).isTrue();

        cancelService.cancel(room, host, job.publicId());

        assertThat(repository.hasTrial(host, room)).isFalse();
    }

    @Test
    void 접수_여부를_모르거나_이미_접수된_작업을_취소해도_체험은_돌려주지_않는다() {
        states.accept(job);
        cancelService.cancel(room, host, job.publicId());
        assertThat(repository.hasTrial(host, room)).isTrue();

        Long other = anonymousMember();
        Long otherRoom = room(other);
        jpegs(otherRoom, other, 2);
        OrganizeJob unknown = startPerson(otherRoom, other);
        var execution = repository.findLatestExecution(unknown.id()).orElseThrow();
        jdbc.update("UPDATE organize_execution SET status = 'DISPATCH_UNKNOWN' WHERE id = ?", execution.id());
        cancelService.cancel(otherRoom, other, unknown.publicId());
        assertThat(repository.hasTrial(other, otherRoom)).isTrue();
    }

    @Test
    void 발송_중에는_이미_전달됐을_수_있으므로_취소해도_체험을_돌려주지_않는다() {
        var execution = repository.findLatestExecution(job.id()).orElseThrow();
        assertThat(repository.claimExecution(execution.id(), clock.instant(), clock.instant().plusSeconds(120))).isTrue();

        OrganizeJob canceled = cancelService.cancel(room, host, job.publicId());

        assertThat(canceled.status()).isEqualTo(OrganizeJobStatus.CANCELED);
        assertThat(repository.hasTrial(host, room)).isTrue();
    }

    @Test
    void 취소_뒤_늦게_온_결과는_버린다() {
        states.accept(job);
        cancelService.cancel(room, host, job.publicId());

        eventService.handle(completed(job, personResult(repository.findPhotos(job.id()).stream()
            .map(photo -> photo.mediaId()).toList(), java.util.List.of())));

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.CANCELED);
    }
}
