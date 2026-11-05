package com.sssok.renewal.application.organization.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sssok.renewal.application.organization.OrganizeH2Test;
import com.sssok.renewal.application.organization.OrganizeServiceTestSupport;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException.Reason;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionRequest;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

// Repository + Service 통합 테스트 (H2). 분석 서버로 보낸 결과(MCP 실행기의 실패 사유)가 어떤 상태로 이어지는지 확인한다.
@OrganizeH2Test
class OrganizeDispatchServiceTest extends OrganizeServiceTestSupport {
    @Autowired
    OrganizeDispatchService dispatchService;
    @Autowired
    OrganizeCancelService cancelService;

    private Long host;
    private Long room;
    private List<Long> ids;
    private OrganizeJob job;
    private OrganizeExecution execution;

    @BeforeEach
    void setUp() {
        host = anonymousMember();
        room = room(host);
        ids = jpegs(room, host, 3);
        job = startPerson(room, host);
        execution = repository.findLatestExecution(job.id()).orElseThrow();
    }

    private OrganizeExecution latest() {
        return repository.findLatestExecution(job.id()).orElseThrow();
    }

    @Test
    void 접수되면_작업은_RUNNING_실행은_ACCEPTED가_된다() {
        dispatchService.dispatch(execution.id());

        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.RUNNING);
        assertThat(latest().status()).isEqualTo(OrganizeExecutionStatus.ACCEPTED);
    }

    @Test
    void 분석_서버에는_실행_ID와_회차_대상_사진과_서명_URL을_보낸다() {
        dispatchService.dispatch(execution.id());

        ArgumentCaptor<OrganizeExecutionRequest> sent = ArgumentCaptor.forClass(OrganizeExecutionRequest.class);
        verify(executor).execute(sent.capture());
        OrganizeExecutionRequest request = sent.getValue();
        assertThat(request.tool().name()).isEqualTo("group_photos_by_person");
        assertThat(request.jobId()).isEqualTo(job.publicId());
        assertThat(request.executionId()).isEqualTo(execution.executionId());
        assertThat(request.attempt()).isEqualTo(1);
        assertThat(request.photos()).extracting(photo -> photo.mediaId()).containsExactlyElementsOf(ids);
        assertThat(request.photos()).extracting(photo -> photo.extension()).containsOnly("jpg");
        assertThat(request.photos()).allSatisfy(photo -> assertThat(photo.source()).startsWith("https://storage.test/"));
    }

    @Test
    void 같은_실행을_두_번_보내려_해도_한_번만_보낸다() {
        dispatchService.dispatch(execution.id());
        dispatchService.dispatch(execution.id());

        verify(executor, times(1)).execute(any());
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"NOT_DELIVERED", "SERVER_BUSY"})
    void 전달하지_못했거나_서버가_일시적으로_거절하면_같은_실행을_나중에_다시_보낸다(Reason reason) {
        willThrow(new OrganizeExecutionException(reason)).given(executor).execute(any());

        dispatchService.dispatch(execution.id());

        assertThat(latest().status()).isEqualTo(OrganizeExecutionStatus.DISPATCH_PENDING);
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.QUEUED);
        Instant next = jdbc.queryForObject("SELECT next_dispatch_at FROM organize_execution WHERE id = ?",
            java.sql.Timestamp.class, execution.id()).toInstant();
        assertThat(next).isAfter(clock.instant());
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"OUTCOME_UNKNOWN", "INVALID_RESPONSE"})
    void 접수_여부를_모르면_불확실로_표시하고_같은_실행_ID로만_다시_보낸다(Reason reason) {
        willThrow(new OrganizeExecutionException(reason)).given(executor).execute(any());

        dispatchService.dispatch(execution.id());

        assertThat(latest().status()).isEqualTo(OrganizeExecutionStatus.DISPATCH_UNKNOWN);
        assertThat(latest().executionId()).isEqualTo(execution.executionId());
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.QUEUED);
    }

    @ParameterizedTest
    @EnumSource(value = Reason.class, names = {"AUTHENTICATION_FAILED", "TOOL_CHANGED", "REJECTED"})
    void 다시_해도_소용없는_거절은_재시도_없이_실패로_닫고_방과_체험을_돌려준다(Reason reason) {
        willThrow(new OrganizeExecutionException(reason)).given(executor).execute(any());

        dispatchService.dispatch(execution.id());

        OrganizeJob failed = reload(job);
        assertThat(failed.status()).isEqualTo(OrganizeJobStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo(reason.name());
        assertThat(latest().status()).isEqualTo(OrganizeExecutionStatus.FAILED);
        assertThat(repository.findActiveByRoom(room)).isEmpty();
        assertThat(repository.hasTrial(host, room)).isFalse();
    }

    @Test
    void 발송_기한을_넘기면_보내지_않고_DISPATCH_TIMEOUT으로_닫는다() {
        clock.advance(Duration.ofMinutes(16));

        dispatchService.dispatch(execution.id());

        assertThat(reload(job).failureCode()).isEqualTo("DISPATCH_TIMEOUT");
        verify(executor, never()).execute(any());
    }

    @Test
    void 대상_사진이_사라졌으면_보내지_않고_TARGET_CHANGED로_닫는다() {
        jdbc.update("DELETE FROM stored_file WHERE id = ?", ids.get(0));

        dispatchService.dispatch(execution.id());

        assertThat(reload(job).failureCode()).isEqualTo("TARGET_CHANGED");
        verify(executor, never()).execute(any());
    }

    @Test
    void 이미_닫힌_작업은_보내지_않고_실행만_닫는다() {
        cancelService.cancel(room, host, job.publicId());

        dispatchService.dispatch(execution.id());

        verify(executor, never()).execute(any());
        assertThat(latest().status()).isEqualTo(OrganizeExecutionStatus.CANCELED);
    }

    @Test
    void 스위퍼는_보낼_때가_된_실행만_집어_간다() {
        willThrow(new OrganizeExecutionException(Reason.NOT_DELIVERED)).given(executor).execute(any());
        dispatchService.dispatch(execution.id());
        org.mockito.Mockito.reset(executor);

        // 스위퍼는 DB 전체에서 보낼 때가 된 실행을 고르므로, 이 작업을 보냈는지만 본다.
        dispatchService.dispatchDue();
        verify(executor, never()).execute(argThat(request -> request.jobId().equals(job.publicId())));

        clock.advance(Duration.ofMinutes(1));
        dispatchService.dispatchDue();
        verify(executor, times(1)).execute(argThat(request -> request.jobId().equals(job.publicId())));
        assertThat(reload(job).status()).isEqualTo(OrganizeJobStatus.RUNNING);
    }
}
