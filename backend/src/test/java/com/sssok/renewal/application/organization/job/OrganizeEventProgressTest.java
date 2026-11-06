package com.sssok.renewal.application.organization.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// RUNNING 이벤트가 작업의 진행을 바꾸는 규칙. 같은 값은 생존 시각만 갱신하고, 바뀐 값만 저장·통지한다.
class OrganizeEventProgressTest {
    private static final Instant T0 = Instant.parse("2026-11-06T00:00:00Z");
    private static final Long JOB_PK = 1L;
    private static final Long ROOM = 2L;
    private static final Long EXECUTION_PK = 10L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID jobId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();
    private final OrganizeJobRepository repository = mock(OrganizeJobRepository.class);
    private final OrganizeChangeNotifier notifier = mock(OrganizeChangeNotifier.class);
    private OrganizeEventService service;

    @BeforeEach
    void setUp() {
        OrganizeExecution execution = new OrganizeExecution(EXECUTION_PK, JOB_PK, executionId, 1,
            OrganizeExecutionStatus.RUNNING, 1, null, T0, T0, T0, null);
        given(repository.findExecutionByExecutionId(executionId)).willReturn(Optional.of(execution));
        given(repository.findLatestExecution(JOB_PK)).willReturn(Optional.of(execution));
        given(repository.markExecutionRunning(eq(EXECUTION_PK), any())).willReturn(true);
        service = new OrganizeEventService(repository, mock(OrganizeResultValidator.class),
            mock(OrganizeJobTransitions.class), notifier, mock(OrganizeJobProperties.class), objectMapper,
            Clock.systemUTC());
    }

    @Test
    void 같은_진행이면_생존_시각만_갱신하고_버전_증가와_알림을_생략한다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "detecting_faces", 24, 60, "PHOTO"));

        var ack = service.handle(running(progress("detecting_faces", 24, 60, "PHOTO")));

        org.assertj.core.api.Assertions.assertThat(ack.accepted()).isTrue();
        verify(repository).markExecutionRunning(eq(EXECUTION_PK), any());
        verify(repository, never()).updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any());
        verifyNoInteractions(notifier);
    }

    @Test
    void 같은_단계에서_처리_수가_늘면_저장하고_알린다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "detecting_faces", 24, 60, "PHOTO"));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);

        service.handle(running(progress("detecting_faces", 25, 60, "PHOTO")));

        verify(repository).updateProgress(eq(JOB_PK), eq(7), eq("detecting_faces"), eq(25), eq(60), eq("PHOTO"), any());
        verify(notifier).changed(JOB_PK, ROOM, Occupancy.UNCHANGED);
    }

    @Test
    void 단계가_바뀌면_수치가_없어도_저장하고_알린다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "detecting_faces", 60, 60, "PHOTO"));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);

        service.handle(running(progress("grouping_faces", null, null, null)));

        verify(repository).updateProgress(eq(JOB_PK), eq(7), eq("grouping_faces"), eq(null), eq(null), eq(null), any());
        verify(notifier).changed(JOB_PK, ROOM, Occupancy.UNCHANGED);
    }

    @Test
    void 첫_RUNNING은_진행_수치가_없어도_저장하고_알린다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.QUEUED, 1, null, null, null, null));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);

        service.handle(running(objectMapper.createObjectNode()));

        verify(repository).updateProgress(eq(JOB_PK), eq(1), eq(null), eq(null), eq(null), eq(null), any());
        verify(notifier).changed(JOB_PK, ROOM, Occupancy.UNCHANGED);
    }

    @Test
    void 단계_정보가_없는_이벤트는_이미_있는_진행을_지우지_않는다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "downloading_photos", 3, 10, "PHOTO"));

        service.handle(running(objectMapper.createObjectNode()));

        verify(repository, never()).updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any());
        verifyNoInteractions(notifier);
    }

    @ParameterizedTest
    @CsvSource({
        "PERSON, detecting_bodies, 5, detecting_faces, 60",
        "PERSON, detecting_faces, 30, detecting_faces, 24",
        "PERSON, detecting_faces, 24, detecting_faces, ",
        "PERSON, exporting, , waiting, ",
        "SIMILAR, extracting_scene_features, 10, loading_scene_model, ",
        "SIMILAR, matching_similar_photos, 4, extracting_center_features, 60"
    })
    void 같은_회차의_지연된_이벤트는_진행을_되돌리지_않는다(OrganizeKind kind, String currentStage, Integer currentProcessed,
                                   String eventStage, Integer eventProcessed) {
        Integer total = currentProcessed == null ? null : 60;
        givenJob(job(kind, OrganizeJobStatus.RUNNING, 7, currentStage, currentProcessed, total,
            currentProcessed == null ? null : "PHOTO"));

        var ack = service.handle(running(kind.name(), progress(eventStage, eventProcessed,
            eventProcessed == null ? null : 60, eventProcessed == null ? null : "PHOTO")));

        org.assertj.core.api.Assertions.assertThat(ack.accepted()).isTrue();
        verify(repository).markExecutionRunning(eq(EXECUTION_PK), any());
        verify(repository, never()).updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any());
        verifyNoInteractions(notifier);
    }

    @Test
    void 순서를_모르는_단계는_거르지_않고_받는다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "exporting", null, null, null));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);

        service.handle(running(progress("some_future_stage", null, null, null)));

        verify(repository).updateProgress(eq(JOB_PK), eq(7), eq("some_future_stage"), eq(null), eq(null), eq(null), any());
    }

    @ParameterizedTest
    @CsvSource({
        "70, 60, PHOTO",
        "-1, 60, PHOTO"
    })
    void 잘못된_수치는_버리고_단계만_저장한다(int processed, int total, String unit) {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "preparing", 1, 60, "PHOTO"));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);

        service.handle(running(progress("detecting_faces", processed, total, unit)));

        verify(repository).updateProgress(eq(JOB_PK), eq(7), eq("detecting_faces"), eq(null), eq(null), eq(null), any());
    }

    @Test
    void 모르는_단위는_null로_저장한다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "preparing", 1, 60, "PHOTO"));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);

        service.handle(running(progress("detecting_faces", 3, 60, "FILE")));

        verify(repository).updateProgress(eq(JOB_PK), eq(7), eq("detecting_faces"), eq(3), eq(60), eq(null), any());
    }

    @Test
    void 단계가_없는_수치는_버린다() {
        ObjectNode withoutStage = objectMapper.createObjectNode().put("processed", 3).put("total", 10)
            .put("unit", "PHOTO");

        var snapshot = running(withoutStage).snapshot();

        org.assertj.core.api.Assertions.assertThat(snapshot)
            .isEqualTo(new OrganizeEvent.Snapshot(null, null, null, null));
    }

    @Test
    void int_범위를_넘는_수치는_줄여서_받지_않고_버린다() {
        // 4294967297 -> 1, 4294967299 -> 3 으로 줄면 멀쩡한 1/3 처럼 보인다.
        ObjectNode flat = objectMapper.createObjectNode().put("stage", "detecting_faces")
            .put("processed", 4294967297L).put("total", 4294967299L).put("unit", "PHOTO");
        ObjectNode legacy = objectMapper.createObjectNode().put("stage", "detecting_faces");
        legacy.putObject("counts").put("processed", 4294967297L).put("total", 4294967299L);

        org.assertj.core.api.Assertions.assertThat(running(flat).snapshot())
            .isEqualTo(new OrganizeEvent.Snapshot("detecting_faces", null, null, null));
        org.assertj.core.api.Assertions.assertThat(running(legacy).snapshot())
            .isEqualTo(new OrganizeEvent.Snapshot("detecting_faces", null, null, null));
    }

    @Test
    void 이전_Python의_counts_형식도_받는다() {
        givenJob(job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "preparing", 1, 3, null));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(true);
        ObjectNode legacy = objectMapper.createObjectNode().put("stage", "detecting_faces");
        legacy.putObject("counts").put("processed", 1).put("total", 3);

        service.handle(running(legacy));

        verify(repository).updateProgress(eq(JOB_PK), eq(7), eq("detecting_faces"), eq(1), eq(3), eq(null), any());
    }

    @Test
    void 읽은_버전이_그_사이_바뀌었으면_다시_읽고_이미_반영된_값이면_더_쓰지_않는다() {
        OrganizeJob stale = job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 7, "waiting", null, null, null);
        OrganizeJob fresh = job(OrganizeKind.PERSON, OrganizeJobStatus.RUNNING, 8, "detecting_faces", 24, 60, "PHOTO");
        given(repository.findById(JOB_PK)).willReturn(Optional.of(stale), Optional.of(fresh));
        given(repository.updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any())).willReturn(false);

        service.handle(running(progress("detecting_faces", 24, 60, "PHOTO")));

        verify(repository, times(1)).updateProgress(anyLong(), anyInt(), any(), any(), any(), any(), any());
        verifyNoInteractions(notifier);
    }

    private void givenJob(OrganizeJob job) {
        given(repository.findById(JOB_PK)).willReturn(Optional.of(job));
    }

    private OrganizeJob job(OrganizeKind kind, OrganizeJobStatus status, int version, String stage, Integer processed,
                            Integer total, String unit) {
        return new OrganizeJob(JOB_PK, jobId, ROOM, 3L, kind, OrganizeTrigger.BUTTON, status, null, "gallery",
            "tool", "설명", 60, 0, version, 0, null, null, null, null, stage, processed, total, unit, null, null, null,
            T0, T0);
    }

    private OrganizeEvent running(JsonNode progress) {
        return running("PERSON", progress);
    }

    private OrganizeEvent running(String jobType, JsonNode progress) {
        return new OrganizeEvent(jobId, ROOM, executionId, 1, jobType, "RUNNING", progress, null, null);
    }

    private JsonNode progress(String stage, Integer processed, Integer total, String unit) {
        ObjectNode node = objectMapper.createObjectNode().put("stage", stage);
        if (processed != null) {
            node.put("processed", processed).put("total", total).put("unit", unit);
        }
        return node;
    }
}
