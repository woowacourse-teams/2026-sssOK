package com.sssok.renewal.application.media.thumbnail;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Source;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


class ThumbnailDispatcherTest {
    final ThumbnailJobService thumbnailJobService = mock(ThumbnailJobService.class);
    final ThumbnailJobCompletionService thumbnailJobCompletionService = mock(ThumbnailJobCompletionService.class);
    final GenerateThumbnailService generator = mock(GenerateThumbnailService.class);
    final AsyncTaskExecutor images = mock(AsyncTaskExecutor.class);
    final AsyncTaskExecutor videos = mock(AsyncTaskExecutor.class);
    final ThumbnailHeartbeat heartbeats = new ThumbnailHeartbeat(thumbnailJobService, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50));
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final ThumbnailDispatcher dispatcher = new ThumbnailDispatcher(thumbnailJobService, thumbnailJobCompletionService, generator, images, videos,
        heartbeats, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50),
        new MediaProcessingMetrics(registry));

    @AfterEach
    void close() { heartbeats.close(); registry.close(); }

    @Test
    void 슬롯을_유지하며_다음_스케줄을_기다리지_않고_세_작업을_연속처리한다() {
        ThumbnailClaim first = claim(ThumbnailKind.IMAGE);
        ThumbnailClaim second = claim(ThumbnailKind.IMAGE);
        ThumbnailClaim third = claim(ThumbnailKind.IMAGE);
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(first), Optional.of(second),
            Optional.of(third), Optional.empty());
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        dispatcher.dispatchPendingJobs();
        dispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(1)).claimNext(ThumbnailKind.IMAGE);
        verifyNoInteractions(generator);
        tasks.getFirst().run();
        verify(generator).generate(first, Source.INITIAL);
        verify(generator).generate(second, Source.INITIAL);
        verify(generator).generate(third, Source.INITIAL);
        verify(images, times(1)).execute(any(Runnable.class));
        assertThat(registry.get("sssok.media.submissions")
            .tags("kind", "image", "source", "initial", "result", "accepted").counter().count()).isEqualTo(3);
        verify(thumbnailJobService, times(4)).claimNext(ThumbnailKind.IMAGE);
        dispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(5)).claimNext(ThumbnailKind.IMAGE);
    }

    @Test
    void 인터럽트되면_다음_작업을_선점하지_않고_슬롯을_반환한다() {
        ThumbnailClaim first = claim(ThumbnailKind.IMAGE);
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(first), Optional.empty());
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        doAnswer(invocation -> { Thread.currentThread().interrupt(); return null; })
            .when(generator).generate(first, Source.INITIAL);
        dispatcher.dispatchPendingJobs();
        try {
            tasks.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(thumbnailJobService, times(1)).claimNext(ThumbnailKind.IMAGE);
        } finally {
            Thread.interrupted();
        }
        dispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(2)).claimNext(ThumbnailKind.IMAGE);
    }

    @Test
    void 연속처리중_선점오류가_나도_슬롯을_반환한다() {
        ThumbnailClaim first = claim(ThumbnailKind.IMAGE);
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(first))
            .thenThrow(new IllegalStateException("DB"))
            .thenReturn(Optional.empty());
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        dispatcher.dispatchPendingJobs();
        Assertions.assertThatThrownBy(() -> tasks.getFirst().run())
            .isInstanceOf(IllegalStateException.class);
        dispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(3)).claimNext(ThumbnailKind.IMAGE);
    }

    @Test
    void 거절된_작업은_대기로_돌리고_다음주기에_다시_제출한다() {
        ThumbnailClaim claim = claim(ThumbnailKind.IMAGE);
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(claim));
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        doThrow(new TaskRejectedException("full")).doNothing().when(images).execute(any(Runnable.class));
        dispatcher.dispatchPendingJobs();
        dispatcher.dispatchPendingJobs();
        verify(thumbnailJobService).returnRejected(claim);
        verify(images, times(2)).execute(any(Runnable.class));
        assertThat(registry.get("sssok.media.submissions")
            .tags("kind", "image", "source", "initial", "result", "rejected").counter().count()).isEqualTo(1);
    }

    @Test
    void 영상과_사진은_각각_전용_실행기에_전달한다() {
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(claim(ThumbnailKind.IMAGE)));
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.of(claim(ThumbnailKind.VIDEO)));
        dispatcher.dispatchPendingJobs();
        verify(images).execute(any(Runnable.class));
        verify(videos).execute(any(Runnable.class));
        verifyNoInteractions(generator);
    }

    @Test
    void 선점_DB_오류에도_슬롯을_반환한다() {
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenThrow(new IllegalStateException("DB"))
            .thenReturn(Optional.empty());
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        Assertions.assertThatThrownBy(dispatcher::dispatchPendingJobs).isInstanceOf(IllegalStateException.class);
        dispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(2)).claimNext(ThumbnailKind.IMAGE);
    }

    @Test
    void 소유권을_잃으면_현재_워커를_중단하고_완료후_늦은_heartbeat는_무시한다() {
        ThumbnailHeartbeat controlledHeartbeat = spy(new ThumbnailHeartbeat(thumbnailJobService, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50)));
        ScheduledFuture<?> heartbeatTask = mock(ScheduledFuture.class);
        List<Runnable> pulses = new ArrayList<>();
        doAnswer(invocation -> {
            pulses.add(invocation.getArgument(0));
            return heartbeatTask;
        }).when(controlledHeartbeat).schedule(any(Runnable.class), anyLong());
        ThumbnailDispatcher controlledDispatcher = new ThumbnailDispatcher(
            thumbnailJobService, thumbnailJobCompletionService, generator, images, videos,
            controlledHeartbeat, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50),
            new MediaProcessingMetrics(registry));
        ThumbnailClaim claimedJob = claim(ThumbnailKind.IMAGE);
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(claimedJob), Optional.empty());
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        when(thumbnailJobService.renewLease(claimedJob)).thenReturn(ThumbnailJobService.Heartbeat.LOST);
        List<Runnable> workerTasks = new ArrayList<>();
        doAnswer(invocation -> { workerTasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        doAnswer(invocation -> { pulses.getFirst().run(); return null; })
            .when(generator).generate(claimedJob, Source.INITIAL);

        controlledDispatcher.dispatchPendingJobs();
        try {
            workerTasks.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(thumbnailJobService, times(1)).claimNext(ThumbnailKind.IMAGE);
            verify(heartbeatTask).cancel(false);
        } finally {
            Thread.interrupted();
        }
        pulses.getFirst().run();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        controlledDispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(2)).claimNext(ThumbnailKind.IMAGE);
    }

    @Test
    void 생성_예외는_재시도가능_실패로_기록하고_heartbeat와_슬롯을_정리한다() {
        ThumbnailHeartbeat controlledHeartbeat = spy(new ThumbnailHeartbeat(thumbnailJobService, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50)));
        ScheduledFuture<?> heartbeatTask = mock(ScheduledFuture.class);
        doReturn(heartbeatTask).when(controlledHeartbeat).schedule(any(Runnable.class), anyLong());
        ThumbnailDispatcher controlledDispatcher = new ThumbnailDispatcher(
            thumbnailJobService, thumbnailJobCompletionService, generator, images, videos,
            controlledHeartbeat, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50),
            new MediaProcessingMetrics(registry));
        ThumbnailClaim claimedJob = claim(ThumbnailKind.IMAGE);
        when(thumbnailJobService.claimNext(ThumbnailKind.IMAGE)).thenReturn(Optional.of(claimedJob), Optional.empty());
        when(thumbnailJobService.claimNext(ThumbnailKind.VIDEO)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("processing failed")).when(generator).generate(claimedJob, Source.INITIAL);
        List<Runnable> workerTasks = new ArrayList<>();
        doAnswer(invocation -> { workerTasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));

        controlledDispatcher.dispatchPendingJobs();
        workerTasks.getFirst().run();

        verify(thumbnailJobCompletionService).fail(claimedJob, false, "WORKER_ERROR");
        verify(heartbeatTask).cancel(false);
        controlledDispatcher.dispatchPendingJobs();
        verify(thumbnailJobService, times(3)).claimNext(ThumbnailKind.IMAGE);
    }

    private ThumbnailClaim claim(ThumbnailKind kind) {
        return new ThumbnailClaim(1L, kind, UUID.randomUUID(), 1, Instant.now());
    }
}
