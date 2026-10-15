package com.sssok.application.media.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sssok.application.media.GenerateThumbnailService;
import com.sssok.common.monitoring.MediaProcessingMetrics;
import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import com.sssok.common.monitoring.MediaProcessingMetrics.Source;
import com.sssok.infrastructure.config.ThumbnailQueueProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class ThumbnailDispatcherTest {
    final ThumbnailJobs jobs = mock(ThumbnailJobs.class);
    final GenerateThumbnailService generator = mock(GenerateThumbnailService.class);
    final AsyncTaskExecutor images = mock(AsyncTaskExecutor.class);
    final AsyncTaskExecutor videos = mock(AsyncTaskExecutor.class);
    final ThumbnailHeartbeat heartbeats = new ThumbnailHeartbeat();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final ThumbnailDispatcher dispatcher = new ThumbnailDispatcher(jobs, generator, images, videos,
        heartbeats, new ThumbnailQueueProperties(1, 1, 5, null, null, null, null, 50),
        new MediaProcessingMetrics(registry));

    @AfterEach
    void close() { heartbeats.close(); registry.close(); }

    @Test
    void 슬롯을_유지하며_다음_스케줄을_기다리지_않고_세_작업을_연속처리한다() {
        var first = claim(Kind.IMAGE);
        var second = claim(Kind.IMAGE);
        var third = claim(Kind.IMAGE);
        when(jobs.claim(Kind.IMAGE)).thenReturn(Optional.of(first), Optional.of(second),
            Optional.of(third), Optional.empty());
        when(jobs.claim(Kind.VIDEO)).thenReturn(Optional.empty());
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        dispatcher.dispatch();
        dispatcher.dispatch();
        verify(jobs, times(1)).claim(Kind.IMAGE);
        verifyNoInteractions(generator);
        tasks.getFirst().run();
        verify(generator).generate(first, Source.INITIAL);
        verify(generator).generate(second, Source.INITIAL);
        verify(generator).generate(third, Source.INITIAL);
        verify(images, times(1)).execute(any(Runnable.class));
        assertThat(registry.get("sssok.media.submissions")
            .tags("kind", "image", "source", "initial", "result", "accepted").counter().count()).isEqualTo(3);
        verify(jobs, times(4)).claim(Kind.IMAGE);
        dispatcher.dispatch();
        verify(jobs, times(5)).claim(Kind.IMAGE);
    }

    @Test
    void 인터럽트되면_다음_작업을_선점하지_않고_슬롯을_반환한다() {
        var first = claim(Kind.IMAGE);
        when(jobs.claim(Kind.IMAGE)).thenReturn(Optional.of(first), Optional.empty());
        when(jobs.claim(Kind.VIDEO)).thenReturn(Optional.empty());
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        doAnswer(invocation -> { Thread.currentThread().interrupt(); return null; })
            .when(generator).generate(first, Source.INITIAL);
        dispatcher.dispatch();
        try {
            tasks.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(jobs, times(1)).claim(Kind.IMAGE);
        } finally {
            Thread.interrupted();
        }
        dispatcher.dispatch();
        verify(jobs, times(2)).claim(Kind.IMAGE);
    }

    @Test
    void 연속처리중_선점오류가_나도_슬롯을_반환한다() {
        var first = claim(Kind.IMAGE);
        when(jobs.claim(Kind.IMAGE)).thenReturn(Optional.of(first))
            .thenThrow(new IllegalStateException("DB"))
            .thenReturn(Optional.empty());
        when(jobs.claim(Kind.VIDEO)).thenReturn(Optional.empty());
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(invocation -> { tasks.add(invocation.getArgument(0)); return null; })
            .when(images).execute(any(Runnable.class));
        dispatcher.dispatch();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tasks.getFirst().run())
            .isInstanceOf(IllegalStateException.class);
        dispatcher.dispatch();
        verify(jobs, times(3)).claim(Kind.IMAGE);
    }

    @Test
    void 거절된_작업은_대기로_돌리고_다음주기에_다시_제출한다() {
        var claim = claim(Kind.IMAGE);
        when(jobs.claim(Kind.IMAGE)).thenReturn(Optional.of(claim));
        when(jobs.claim(Kind.VIDEO)).thenReturn(Optional.empty());
        doThrow(new TaskRejectedException("full")).doNothing().when(images).execute(any(Runnable.class));
        dispatcher.dispatch();
        dispatcher.dispatch();
        verify(jobs).rejected(claim);
        verify(images, times(2)).execute(any(Runnable.class));
        assertThat(registry.get("sssok.media.submissions")
            .tags("kind", "image", "source", "initial", "result", "rejected").counter().count()).isEqualTo(1);
    }

    @Test
    void 영상과_사진은_각각_전용_실행기에_전달한다() {
        when(jobs.claim(Kind.IMAGE)).thenReturn(Optional.of(claim(Kind.IMAGE)));
        when(jobs.claim(Kind.VIDEO)).thenReturn(Optional.of(claim(Kind.VIDEO)));
        dispatcher.dispatch();
        verify(images).execute(any(Runnable.class));
        verify(videos).execute(any(Runnable.class));
        verifyNoInteractions(generator);
    }

    @Test
    void 선점_DB_오류에도_슬롯을_반환한다() {
        when(jobs.claim(Kind.IMAGE)).thenThrow(new IllegalStateException("DB"))
            .thenReturn(Optional.empty());
        when(jobs.claim(Kind.VIDEO)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(dispatcher::dispatch).isInstanceOf(IllegalStateException.class);
        dispatcher.dispatch();
        verify(jobs, times(2)).claim(Kind.IMAGE);
    }

    private ThumbnailClaim claim(Kind kind) {
        return new ThumbnailClaim(1L, kind, UUID.randomUUID(), 1, Instant.now());
    }
}
