package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 어떤 실패를 어떻게 다시 시도하는지, 상한·대기·충돌 규칙을 확인한다.
@OrganizeH2Test
class OrganizeRetryServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeRetryService retryService;
    @Autowired
    OrganizeLivenessService livenessService;
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
        ids = jpegs(room, host, 2);
        job = startPerson(room, host);
    }

    private ErrorCode errorOf(Runnable action) {
        return org.junit.jupiter.api.Assertions.assertThrows(SssOkException.class, action::run).errorCode();
    }

    private Long executions() {
        return jdbc.queryForObject("SELECT count(*) FROM organize_execution WHERE job_id = ?", Long.class, job.id());
    }

    // 분석 서버가 실패로 끝냈다고 알려 온 뒤 첫 대기 시간(5초)이 지난 상태.
    private void analysisFailedAndWaited() {
        states.analysisFailed(job);
        clock.advance(Duration.ofSeconds(6));
    }

    @Test
    void 분석_서버가_실패로_끝낸_작업은_같은_작업에_새_실행_회차를_만든다() {
        OrganizeExecution first = repository.findLatestExecution(job.id()).orElseThrow();
        analysisFailedAndWaited();
        assertThat(reload(job).failureCode()).isEqualTo("ANALYSIS_FAILED");

        OrganizeRetryService.Result result = retryService.retry(room, host, job.publicId(), key());

        assertThat(result.started()).isTrue();
        OrganizeJob retried = reload(job);
        assertThat(retried.status()).isEqualTo(OrganizeJobStatus.QUEUED);
        assertThat(retried.failureCode()).isNull();
        assertThat(retried.retryCount()).isEqualTo(1);
        assertThat(retried.finishedAt()).isNull();
        OrganizeExecution second = repository.findLatestExecution(job.id()).orElseThrow();
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(second.executionId()).isNotEqualTo(first.executionId());
        assertThat(second.status()).isEqualTo(OrganizeExecutionStatus.DISPATCH_PENDING);
        assertThat(repository.findActiveByRoom(room)).isPresent();
        assertThat(repository.findRoomState(room)).get().extracting("busy").isEqualTo(true);
    }

    @Test
    void 실패_직후에는_대기_시간이_지나야_다시_시도할_수_있다() {
        states.analysisFailed(job);

        assertThatThrownBy(() -> retryService.retry(room, host, job.publicId(), key()))
            .isInstanceOfSatisfying(OrganizeRateLimitedException.class,
                e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 6L));
        assertThat(executions()).isEqualTo(1);
    }

    @Test
    void 새_실행을_만드는_재시도는_작업당_3회까지다() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            analysisFailedAndWaited();
            clock.advance(Duration.ofMinutes(1));
            retryService.retry(room, host, job.publicId(), key());
            assertThat(reload(job).retryCount()).isEqualTo(attempt);
            // 익명 체험이 재시도마다 되살아나야 하므로 분석 서버가 받은 상태로 이어 간다.
        }
        states.analysisFailed(job);
        clock.advance(Duration.ofMinutes(1));

        assertThat(errorOf(() -> retryService.retry(room, host, job.publicId(), key())))
            .isEqualTo(ErrorCode.ORGANIZE_RETRY_NOT_ALLOWED);
        assertThat(executions()).isEqualTo(4);
    }

    @Test
    void 전달만_실패한_작업은_같은_실행_ID로_다시_보내고_횟수에_넣지_않는다() {
        OrganizeExecution first = repository.findLatestExecution(job.id()).orElseThrow();
        clock.advance(Duration.ofMinutes(16));
        livenessService.sweep();
        assertThat(reload(job).failureCode()).isEqualTo("DISPATCH_TIMEOUT");
        clock.advance(Duration.ofSeconds(6));

        OrganizeRetryService.Result result = retryService.retry(room, host, job.publicId(), key());

        assertThat(result.started()).isTrue();
        assertThat(reload(job).retryCount()).isZero();
        OrganizeExecution again = repository.findLatestExecution(job.id()).orElseThrow();
        assertThat(again.executionId()).isEqualTo(first.executionId());
        assertThat(again.attempt()).isEqualTo(1);
        assertThat(again.status()).isEqualTo(OrganizeExecutionStatus.DISPATCH_PENDING);
        assertThat(executions()).isEqualTo(1);
    }

    @Test
    void 원격_실행이_끝났는지_모르는_실패는_다시_시도할_수_없다() {
        states.accept(job);
        clock.advance(Duration.ofMinutes(31));
        livenessService.sweep();
        assertThat(reload(job).failureCode()).isEqualTo("NOT_STARTED");

        assertThat(errorOf(() -> retryService.retry(room, host, job.publicId(), key())))
            .isEqualTo(ErrorCode.ORGANIZE_RETRY_NOT_ALLOWED);
        assertThat(executions()).isEqualTo(1);
    }

    @Test
    void 취소_만료된_작업은_다시_시도할_수_없다() {
        states.accept(job);
        cancelService.cancel(room, host, job.publicId());

        assertThat(errorOf(() -> retryService.retry(room, host, job.publicId(), key())))
            .isEqualTo(ErrorCode.ORGANIZE_RETRY_NOT_ALLOWED);
    }

    @Test
    void 이미_진행_중이거나_검토_중이면_새_실행_없이_최신_작업만_돌려준다() {
        OrganizeRetryService.Result queued = retryService.retry(room, host, job.publicId(), key());
        assertThat(queued.started()).isFalse();
        assertThat(queued.job().status()).isEqualTo(OrganizeJobStatus.QUEUED);

        states.ready(job);
        OrganizeRetryService.Result ready = retryService.retry(room, host, job.publicId(), key());
        assertThat(ready.started()).isFalse();
        assertThat(ready.job().status()).isEqualTo(OrganizeJobStatus.READY);
        assertThat(executions()).isEqualTo(1);
    }

    @Test
    void 같은_키로_다시_보내면_실행을_더_만들지_않는다() {
        analysisFailedAndWaited();
        String key = key();

        retryService.retry(room, host, job.publicId(), key);
        OrganizeRetryService.Result replay = retryService.retry(room, host, job.publicId(), key);

        assertThat(replay.started()).isTrue();
        assertThat(executions()).isEqualTo(2);
    }

    @Test
    void 그_사이_다른_작업이_방을_차지했으면_거절하고_실패_상태를_유지한다() {
        analysisFailedAndWaited();
        Long guest = registeredMember();
        clock.advance(Duration.ofMinutes(1));
        startService.startByKind(room, guest, com.sssok.renewal.domain.organization.OrganizeKind.SIMILAR, key());

        assertThatThrownBy(() -> retryService.retry(room, host, job.publicId(), key()))
            .isInstanceOf(OrganizeJobInProgressException.class);
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.FAILED);
        assertThat(executions()).isEqualTo(1);
    }

    @Test
    void 대상_사진이_사라졌으면_새_작업을_접수해야_한다() {
        analysisFailedAndWaited();
        jdbc.update("DELETE FROM stored_file WHERE id = ?", ids.get(0));

        assertThat(errorOf(() -> retryService.retry(room, host, job.publicId(), key())))
            .isEqualTo(ErrorCode.ORGANIZE_TARGET_CHANGED);
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.FAILED);
    }

    @Test
    void 익명_회원은_돌려받은_체험을_다시_확보한다() {
        analysisFailedAndWaited();
        assertThat(repository.hasTrial(host, room)).isFalse();

        retryService.retry(room, host, job.publicId(), key());

        assertThat(repository.hasTrial(host, room)).isTrue();
    }

    @Test
    void 돌려받은_체험을_같은_방의_다른_작업이_쓰면_재시도할_수_없다() {
        analysisFailedAndWaited();
        OrganizeJob other = startPerson(room, host);
        states.cancelAfterAccepted(other, host);

        assertThat(errorOf(() -> retryService.retry(room, host, job.publicId(), key())))
            .isEqualTo(ErrorCode.ORGANIZE_TRIAL_EXHAUSTED);
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.FAILED);
    }

    @Test
    void 남의_작업이나_없는_작업은_404다() {
        Long guest = registeredMember();

        assertThat(errorOf(() -> retryService.retry(room, guest, job.publicId(), key())))
            .isEqualTo(ErrorCode.ORGANIZE_JOB_NOT_FOUND);
    }

    @Test
    void 키가_없으면_거절한다() {
        analysisFailedAndWaited();

        assertThat(errorOf(() -> retryService.retry(room, host, job.publicId(), null)))
            .isEqualTo(ErrorCode.INVALID_PARAM);
    }
}
