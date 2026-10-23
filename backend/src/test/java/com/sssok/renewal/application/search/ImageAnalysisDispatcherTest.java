package com.sssok.renewal.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.sssok.renewal.outgoing.config.ImageSearchProperties;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository.AnalysisAttempt;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ImageAnalysisDispatcherTest {
    private final MediaSearchDocumentRepository documents = mock(MediaSearchDocumentRepository.class);
    private final AnalyzeImageService analyzer = mock(AnalyzeImageService.class);
    private final ImageSearchProperties properties = new ImageSearchProperties(
        Instant.EPOCH, 2, 20, 20, 3, null, null, null, null);

    @Test
    void 한번_깨우면_20개를_넘는_작업도_동시성_상한_안에서_연속_처리한다() throws Exception {
        var queue = new ConcurrentLinkedQueue<AnalysisAttempt>();
        for (long id = 1; id <= 60; id++) { queue.add(new AnalysisAttempt(id, 1)); }
        when(documents.claimNext(any(), anyInt())).thenAnswer(call -> Optional.ofNullable(queue.poll()));
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(60);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        doAnswer(call -> {
            int count = active.incrementAndGet();
            maximum.accumulateAndGet(count, Math::max);
            entered.countDown();
            try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                active.decrementAndGet();
                completed.countDown();
            }
            return null;
        }).when(analyzer).analyze(any(AnalysisAttempt.class));
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.initialize();
        try {
            var dispatcher = new ImageAnalysisDispatcher(executor, analyzer, documents, properties, Clock.systemUTC());
            dispatcher.dispatch();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 100; i++) { dispatcher.dispatch(); }
            release.countDown();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum.get()).isEqualTo(2);
            assertThat(queue).isEmpty();
            verify(analyzer, times(60)).analyze(any(AnalysisAttempt.class));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void 제출_거절은_DB를_선점하지_않고_슬롯을_반환한다() {
        var executor = mock(AsyncTaskExecutor.class);
        doThrow(new TaskRejectedException("shutdown")).when(executor).execute(any(Runnable.class));
        var dispatcher = new ImageAnalysisDispatcher(executor, analyzer, documents, properties, Clock.systemUTC());
        dispatcher.dispatch();
        dispatcher.dispatch();
        verify(executor, times(2)).execute(any(Runnable.class));
        verifyNoInteractions(documents, analyzer);
    }

    @Test
    void DB오류와_빈큐_이후에도_다음_배정이_가능하다() {
        var executor = mock(AsyncTaskExecutor.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
            .when(executor).execute(any(Runnable.class));
        when(documents.claimNext(any(), anyInt()))
            .thenThrow(new IllegalStateException("database unavailable"))
            .thenReturn(Optional.empty());
        var dispatcher = new ImageAnalysisDispatcher(executor, analyzer, documents, properties, Clock.systemUTC());
        dispatcher.dispatch();
        when(documents.claimNext(any(), anyInt()))
            .thenReturn(Optional.of(new AnalysisAttempt(1L, 1)), Optional.empty());
        dispatcher.dispatch();
        verify(analyzer).analyze(new AnalysisAttempt(1L, 1));
    }
}
