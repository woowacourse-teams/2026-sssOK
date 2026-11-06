package com.sssok.renewal.incoming.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.EventPublisherPort;
import com.sssok.renewal.application.organization.job.OrganizeJobChangedEvent;
import com.sssok.renewal.application.organization.job.OrganizeRetryPolicy;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeJobStatus;
import com.sssok.renewal.domain.organization.OrganizeKind;
import com.sssok.renewal.domain.organization.OrganizeTrigger;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrganizeJobEventListenerTest {
    private static final Long ROOM_ID = 10L;
    private static final Long REQUESTER_ID = 20L;
    private static final Instant T0 = Instant.parse("2026-11-04T03:00:00Z");

    private final EventPublisherPort publisher = mock(EventPublisherPort.class);
    private final OrganizeJobRepository repository = mock(OrganizeJobRepository.class);
    private final OrganizeRetryPolicy retryPolicy = new OrganizeRetryPolicy(
        new OrganizeJobProperties(true, null, null, null, null, null, null, null, null, null, null, null, null, "t"));
    private final OrganizeJobEventListener listener = new OrganizeJobEventListener(publisher, repository, retryPolicy);

    private OrganizeJob job(OrganizeJobStatus status, String failureCode, int retryCount) {
        return new OrganizeJob(1L, UUID.randomUUID(), ROOM_ID, REQUESTER_ID, OrganizeKind.PERSON,
            OrganizeTrigger.BUTTON, status, null, "gallery", "tool", "설명", 5, 0, 4, retryCount, failureCode, null,
            null, null, "analyzing", 18, 40, "PHOTO", null, null, T0, T0, T0);
    }

    @Test
    void 점유가_바뀌지_않은_변화는_요청자에게만_작업_상세를_보낸다() {
        given(repository.findById(1L)).willReturn(Optional.of(job(OrganizeJobStatus.RUNNING, null, 0)));

        listener.handle(new OrganizeJobChangedEvent(1L, ROOM_ID, null, false));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(ROOM_ID), eq("organize.job.updated"), payload.capture(), eq(REQUESTER_ID));
        verify(publisher, never()).publish(any(), eq("organize.room.changed"), any(), any());
        OrganizeJobEventListener.JobUpdated updated = (OrganizeJobEventListener.JobUpdated) payload.getValue();
        assertThat(updated.status()).isEqualTo("RUNNING");
        assertThat(updated.jobVersion()).isEqualTo(4);
        assertThat(updated.attempt()).isEqualTo(1);
        assertThat(updated.progress()).isEqualTo(new OrganizeJobResponse.Progress("analyzing", 18, 40, "PHOTO"));
        assertThat(updated.retryable()).isFalse();
    }

    @Test
    void 점유가_바뀌면_방_전체에는_busy와_방_버전만_보낸다() {
        given(repository.findById(1L)).willReturn(Optional.of(job(OrganizeJobStatus.QUEUED, null, 0)));

        listener.handle(new OrganizeJobChangedEvent(1L, ROOM_ID, 8L, true));

        ArgumentCaptor<Object> room = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(ROOM_ID), eq("organize.room.changed"), room.capture());
        assertThat(room.getValue()).isEqualTo(new OrganizeJobEventListener.RoomChanged(ROOM_ID, 8L, true));
        verify(publisher).publish(eq(ROOM_ID), eq("organize.job.updated"), any(), eq(REQUESTER_ID));
    }

    @Test
    void 실패한_작업은_사유와_재시도_정보를_함께_보낸다() {
        given(repository.findById(1L))
            .willReturn(Optional.of(job(OrganizeJobStatus.FAILED, "ANALYSIS_FAILED", 1)));

        listener.handle(new OrganizeJobChangedEvent(1L, ROOM_ID, 9L, false));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(ROOM_ID), eq("organize.job.updated"), payload.capture(), eq(REQUESTER_ID));
        OrganizeJobEventListener.JobUpdated updated = (OrganizeJobEventListener.JobUpdated) payload.getValue();
        assertThat(updated.failureCode()).isEqualTo("ANALYSIS_FAILED");
        assertThat(updated.attempt()).isEqualTo(2);
        assertThat(updated.retryable()).isTrue();
        assertThat(updated.retryAvailableAt()).isEqualTo(T0.plusSeconds(15));
    }

    @Test
    void 알림을_보내지_못해도_예외를_밖으로_내보내지_않는다() {
        given(repository.findById(1L)).willReturn(Optional.of(job(OrganizeJobStatus.RUNNING, null, 0)));
        willThrow(new IllegalStateException("직렬화 실패")).given(publisher).publish(any(), any(), any(), any());

        listener.handle(new OrganizeJobChangedEvent(1L, ROOM_ID, null, false));
    }

    @Test
    void 작업이_사라졌으면_작업_알림은_보내지_않는다() {
        given(repository.findById(1L)).willReturn(Optional.empty());

        listener.handle(new OrganizeJobChangedEvent(1L, ROOM_ID, null, false));

        verify(publisher, never()).publish(any(), any(), any(), any());
    }
}
