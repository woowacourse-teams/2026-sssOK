package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 스위퍼가 하는 일(생존 감지·검토 만료·보관 기한 정리)을 시계를 움직여 확인한다.
@OrganizeH2Test
class OrganizeLivenessServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeLivenessService livenessService;
    @Autowired
    OrganizeEventService eventService;

    private Long host;
    private Long room;
    private List<Long> ids;
    private OrganizeJob job;

    @BeforeEach
    void setUp() {
        host = registeredMember();
        room = room(host);
        ids = jpegs(room, host, 2);
        job = startPerson(room, host);
    }

    @Test
    void 접수_뒤_첫_RUNNING이_30분_안에_없으면_NOT_STARTED로_닫는다() {
        states.accept(job);
        clock.advance(Duration.ofMinutes(29));
        livenessService.sweep();
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.RUNNING);

        clock.advance(Duration.ofMinutes(2));
        livenessService.sweep();

        assertThat(reload(job).failureCode()).isEqualTo("NOT_STARTED");
        assertThat(repository.findActiveByRoom(room)).isEmpty();
    }

    @Test
    void 마지막_이벤트_뒤_3분이_지나면_LOST로_닫고_늦은_결과는_버린다() {
        eventService.handle(running(job));
        clock.advance(Duration.ofMinutes(4));

        livenessService.sweep();

        assertThat(reload(job).failureCode()).isEqualTo("LOST");
        eventService.handle(completed(job, personResult(ids, List.of())));
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.FAILED);
    }

    @Test
    void 이벤트가_이어져도_실행이_15분을_넘으면_TIMEOUT으로_닫는다() {
        eventService.handle(running(job));
        for (int i = 0; i < 2; i++) {
            clock.advance(Duration.ofMinutes(7));
            eventService.handle(running(job));
        }
        clock.advance(Duration.ofMinutes(2));

        livenessService.sweep();

        assertThat(reload(job).failureCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void 발송_기한을_넘기면_DISPATCH_TIMEOUT으로_닫는다() {
        clock.advance(Duration.ofMinutes(16));

        livenessService.sweep();

        assertThat(reload(job).failureCode()).isEqualTo("DISPATCH_TIMEOUT");
    }

    @Test
    void 진행_중인_작업은_건드리지_않는다() {
        eventService.handle(running(job));
        clock.advance(Duration.ofMinutes(1));

        livenessService.sweep();

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.RUNNING);
    }

    @Test
    void 검토_기한이_지난_READY는_만료시키고_방을_푼다() {
        states.ready(job);
        clock.advance(Duration.ofMinutes(29));
        livenessService.sweep();
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.READY);

        clock.advance(Duration.ofMinutes(2));
        livenessService.sweep();

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.EXPIRED);
        assertThat(repository.findActiveByRoom(room)).isEmpty();
        assertThat(repository.findRoomState(room)).get().extracting("busy").isEqualTo(false);
    }

    @Test
    void 결과_본문은_보관_기한까지_남고_그_뒤에_비운다_작업_이력은_남긴다() {
        states.ready(job);
        clock.advance(Duration.ofDays(2));
        livenessService.sweep();
        assertThat(reload(job).hasResult()).isTrue();

        clock.advance(Duration.ofDays(1).plusMinutes(1));
        livenessService.sweep();

        OrganizeJob purged = reload(job);
        assertThat(purged.hasResult()).isFalse();
        assertThat(purged.status()).isEqualTo(OrganizeJobStatus.EXPIRED);
    }

    @Test
    void 멱등_키는_24시간_뒤에_지운다() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_idempotency WHERE room_id = ?", Long.class, room))
            .isEqualTo(1);
        clock.advance(Duration.ofHours(23));
        livenessService.sweep();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_idempotency WHERE room_id = ?", Long.class, room))
            .isEqualTo(1);

        clock.advance(Duration.ofHours(2));
        livenessService.sweep();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM organize_idempotency WHERE room_id = ?", Long.class, room))
            .isZero();
    }
}
