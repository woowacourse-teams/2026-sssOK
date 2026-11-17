package com.sssok.renewal.outgoing.monitoring;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailTraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 유한한 단계 이름만 계측한다. 파일 경로, URL, 사용자 ID, 예외 메시지는 내보내지 않는다. */
@Component
public class UploadTracing {
    public enum Stage {
        DOWNLOAD, DERIVE, ORIENTATION, DECODE, RESIZE, ENCODE, METADATA,
        VIDEO_EXTRACT, UPLOAD_THUMBNAIL, UPLOAD_PREVIEW, COMPLETE, READY_READ, READY_PUBLISH
    }

    private final Tracer tracer;
    private final Propagator propagator;
    private final MeterRegistry registry;

    @Autowired
    public UploadTracing(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator, MeterRegistry registry) {
        this(tracer.getIfAvailable(() -> Tracer.NOOP), propagator.getIfAvailable(() -> Propagator.NOOP), registry);
    }

    public UploadTracing(Tracer tracer, Propagator propagator, MeterRegistry registry) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.registry = registry;
    }

    public ThumbnailTraceContext capture() {
        Span current = tracer.currentSpan();
        if (current == null) {
            return ThumbnailTraceContext.EMPTY;
        }
        Map<String, String> headers = new HashMap<>();
        propagator.inject(current.context(), headers, Map::put);
        return new ThumbnailTraceContext(headers.get("traceparent"), headers.get("tracestate"));
    }

    public <T, E extends Throwable> T head(ThumbnailTraceContext context, CheckedSupplier<T, E> action) throws E {
        return inSpan(parent(context).name("upload.registration.head_worker").start(), action);
    }

    public <T, E extends Throwable> T registration(UploadRegistrationMetrics.Stage stage, CheckedSupplier<T, E> action) throws E {
        return inSpan(tracer.nextSpan().name("upload.registration." + lower(stage)).start(), action);
    }

    public <E extends Throwable> MediaProcessingMetrics.Result attempt(ThumbnailClaim claim,
            CheckedSupplier<MediaProcessingMetrics.Result, E> action) throws E {
        Duration wait = Duration.between(claim.availableAt(), claim.startedAt());
        if (wait.isNegative()) {
            wait = Duration.ZERO;
        }
        String kind = lower(claim.kind());
        String attempt = claim.attempt() > 1 ? "retry" : "first";
        Timer.builder("sssok.upload.queue.wait").tags("kind", kind, "attempt", attempt)
            .register(registry).record(wait);
        Span span = parent(claim.traceContext()).name("upload.processing")
            .tag("media.kind", kind).tag("processing.attempt", claim.attempt())
            .tag("queue.wait.ms", wait.toMillis()).start();
        return inSpan(span, () -> {
            MediaProcessingMetrics.Result result = action.get();
            span.tag("processing.result", lower(result));
            if (result == MediaProcessingMetrics.Result.RETRYABLE || result == MediaProcessingMetrics.Result.PERMANENT_FAILURE) {
                span.error(new IllegalStateException(lower(result)));
            }
            return result;
        });
    }

    public <T, E extends Throwable> T stage(Stage stage, CheckedSupplier<T, E> action) throws E {
        Span span = tracer.nextSpan().name("upload.processing." + lower(stage)).start();
        Timer.Sample timer = Timer.start(registry);
        String outcome = "error";
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            T result = action.get();
            outcome = "success";
            return result;
        } catch (Throwable failure) {
            span.error(new IllegalStateException(failure.getClass().getSimpleName()));
            throw failure;
        } finally {
            timer.stop(Timer.builder("sssok.upload.processing.stage")
                .tags("stage", lower(stage), "outcome", outcome).register(registry));
            span.end();
        }
    }

    private Span.Builder parent(ThumbnailTraceContext context) {
        if (context == null || context.traceparent() == null) {
            // 복구 작업처럼 문맥이 없으면 스케줄러의 트레이스에 연결하지 않는다.
            return tracer.spanBuilder().setNoParent();
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", context.traceparent());
        if (context.tracestate() != null) {
            headers.put("tracestate", context.tracestate());
        }
        return propagator.extract(headers, Map::get);
    }

    private <T, E extends Throwable> T inSpan(Span span, CheckedSupplier<T, E> action) throws E {
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } catch (Throwable failure) {
            span.error(new IllegalStateException(failure.getClass().getSimpleName()));
            throw failure;
        } finally {
            span.end();
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface CheckedSupplier<T, E extends Throwable> {
        T get() throws E;
    }
}
