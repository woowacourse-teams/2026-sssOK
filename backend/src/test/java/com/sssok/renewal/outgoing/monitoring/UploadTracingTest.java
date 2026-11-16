package com.sssok.renewal.outgoing.monitoring;

import com.sssok.renewal.application.media.upload.complete.UploadHeadWorkers;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailTraceContext;
import com.sssok.renewal.outgoing.config.UploadHeadProperties;
import com.sssok.support.RecordingUploadTracing;
import io.micrometer.tracing.Span;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static org.assertj.core.api.Assertions.*;

class UploadTracingTest {
    @Test
    void 종료된_등록_요청과_첫_처리_재시도를_같은_트레이스로_잇는다() throws Exception {
        try (var recording = new RecordingUploadTracing(); var worker = Executors.newSingleThreadExecutor()) {
            Span request = recording.tracer.nextSpan().name("request").start();
            ThumbnailTraceContext context;
            try (var scope = recording.tracer.withSpan(request)) {
                context = recording.tracing.capture();
            }
            request.end();
            for (int attempt : List.of(1, 2)) {
                worker.submit(() -> recording.tracing.attempt(claim(context, attempt), () ->
                    recording.tracing.stage(UploadTracing.Stage.DOWNLOAD, () -> MediaProcessingMetrics.Result.SUCCESS)))
                    .get(5, TimeUnit.SECONDS);
                assertThat(worker.submit(recording.tracer::currentSpan).get()).isNull();
            }
            assertThat(recording.spans).hasSize(5).allSatisfy(span ->
                assertThat(span.getTraceId()).isEqualTo(request.context().traceId()));
            assertThat(recording.spans.stream().filter(span -> span.getName().equals("upload.processing")))
                .allSatisfy(span -> {
                    assertThat(span.getParentSpanId()).isEqualTo(request.context().spanId());
                    assertThat(span.getAttributes().get(AttributeKey.longKey("queue.wait.ms"))).isEqualTo(2000L);
                });
            assertThat(recording.registry.get("sssok.upload.queue.wait").tag("attempt", "retry")
                .timer().totalTime(TimeUnit.SECONDS)).isEqualTo(2);
        }
    }

    @Test
    void 미샘플링_요청은_큐를_건너도_샘플링하지_않지만_메트릭은_기록한다() {
        try (var recording = new RecordingUploadTracing()) {
            var context = new ThumbnailTraceContext("00-12345678901234567890123456789012-1234567890123456-00", null);
            recording.tracing.attempt(claim(context, 1), () -> recording.tracing.stage(
                UploadTracing.Stage.DOWNLOAD, () -> MediaProcessingMetrics.Result.SUCCESS));
            assertThat(recording.spans).isEmpty();
            assertThat(recording.registry.get("sssok.upload.processing.stage").timer().count()).isEqualTo(1);
        }
    }

    @Test
    void 예외에도_문맥을_복원하고_URL이_포함된_원본_예외는_내보내지_않는다() {
        try (var recording = new RecordingUploadTracing()) {
            Span outer = recording.tracer.nextSpan().name("outer").start();
            try (var scope = recording.tracer.withSpan(outer)) {
                assertThatThrownBy(() -> recording.tracing.stage(UploadTracing.Stage.DOWNLOAD, () -> {
                    throw new IllegalArgumentException("https://private/file?token=secret");
                })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("secret");
                assertThat(recording.tracer.currentSpan().context().spanId()).isEqualTo(outer.context().spanId());
            } finally {
                outer.end();
            }
            assertThat(recording.spans.getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(recording.spans.getFirst().getEvents().toString()).doesNotContain("secret", "https://private");
            assertThat(recording.tracer.currentSpan()).isNull();
        }
    }

    @Test
    void 기존_작업은_스케줄러와_별개로_시작한다() {
        try (var recording = new RecordingUploadTracing()) {
            Span scheduler = recording.tracer.nextSpan().name("scheduler").start();
            try (var scope = recording.tracer.withSpan(scheduler)) {
                recording.tracing.attempt(claim(ThumbnailTraceContext.EMPTY, 1), () -> MediaProcessingMetrics.Result.RETRYABLE);
            } finally {
                scheduler.end();
            }
            var processing = recording.spans.getFirst();
            assertThat(processing.getTraceId()).isNotEqualTo(scheduler.context().traceId());
            assertThat(processing.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        }
    }

    @Test
    void 실제_HEAD_풀에_부모가_전파되고_다음_작업에는_남지_않는다() throws Exception {
        try (var recording = new RecordingUploadTracing();
             var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(TracingProxyConfiguration.class);
            context.registerBean(UploadTracingAspect.class, () -> new UploadTracingAspect(
                recording.tracing, new MediaProcessingMetrics(recording.registry)));
            context.registerBean("uploadHeadTaskExecutor", ThreadPoolTaskExecutor.class, () -> {
                var executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(1);
                executor.setMaxPoolSize(1);
                return executor;
            });
            context.refresh();
            var executor = context.getBean(ThreadPoolTaskExecutor.class);
            var workers = new UploadHeadWorkers(executor, UploadHeadProperties.defaults(), recording.registry);
            Span request = recording.tracer.nextSpan().name("request").start();
            try (var scope = recording.tracer.withSpan(request)) {
                assertThat(workers.map(List.of(1, 2), ignored -> recording.tracer.currentSpan().context().traceId()))
                    .containsExactly(request.context().traceId(), request.context().traceId());
            } finally {
                request.end();
            }
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var blocker = executor.getThreadPoolExecutor().submit(() -> {
                entered.countDown();
                release.await();
                return null;
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var cancelled = executor.submit(() -> "cancelled");
                assertThat(cancelled.cancel(true)).isTrue();
                executor.getThreadPoolExecutor().purge();
                assertThat(executor.getQueueSize()).isZero();
            } finally {
                release.countDown();
                blocker.get(5, TimeUnit.SECONDS);
            }
            assertThat(executor.getThreadPoolExecutor().submit(recording.tracer::currentSpan).get(5, TimeUnit.SECONDS)).isNull();
            assertThat(recording.spans.stream().filter(span -> span.getName().equals("upload.registration.head_worker")))
                .hasSize(2).allSatisfy(span -> assertThat(span.getParentSpanId()).isEqualTo(request.context().spanId()));
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TracingProxyConfiguration {}

    private ThumbnailClaim claim(ThumbnailTraceContext context, int attempt) {
        Instant now = Instant.now();
        return new ThumbnailClaim(1L, ThumbnailKind.IMAGE, UUID.randomUUID(), attempt, now, now.minusSeconds(2), context);
    }
}
