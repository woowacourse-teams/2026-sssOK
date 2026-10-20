package com.sssok.renewal.outgoing.monitoring;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component("renewalMediaProcessingMetrics")
public class MediaProcessingMetrics {

    public enum Source { INITIAL, SWEEPER }

    public enum Result { SUCCESS, RETRYABLE, PERMANENT_FAILURE, COMPLETED_WITHOUT_THUMBNAIL, SKIPPED }

    private final MeterRegistry registry;
    private final AtomicInteger sweepFound = new AtomicInteger();
    private final AtomicLong sweepLastSuccess = new AtomicLong();

    public MediaProcessingMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (ThumbnailKind kind : ThumbnailKind.values()) {
            for (Source source : Source.values()) {
                for (Result result : Result.values()) {
                    timer(kind, source, result);
                }
                for (String result : new String[]{"accepted", "rejected"}) {
                    registry.counter("sssok.media.submissions", "kind", tag(kind),
                        "source", tag(source), "result", result);
                }
            }
        }
        for (ThumbnailKind kind : ThumbnailKind.values()) {
            Timer.builder("sssok.thumbnail.ready.latency")
                .tag("kind", tag(kind))
                .serviceLevelObjectives(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(10),
                    Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                    Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30))
                .register(registry);
        }
        for (String name : List.of("retried", "failed", "recovered")) {
            registry.counter("sssok.thumbnail." + name);
        }
        Gauge.builder("sssok.media.sweep.found", sweepFound, AtomicInteger::get)
            .description("Media selected by the last sweep, bounded by sweep batch size").register(registry);
        Gauge.builder("sssok.media.sweep.last.success", sweepLastSuccess, AtomicLong::get)
            .baseUnit("seconds").description("Unix time of the last completed sweep").register(registry);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void completed(Timer.Sample sample, ThumbnailKind kind, Source source, Result result) {
        sample.stop(timer(kind, source, result));
    }

    public void submitted(ThumbnailKind kind, Source source, boolean accepted) {
        registry.counter("sssok.media.submissions", "kind", tag(kind), "source", tag(source),
            "result", accepted ? "accepted" : "rejected").increment();
    }

    public void swept(int found) {
        sweepFound.set(found);
        sweepLastSuccess.set(registry.config().clock().wallTime() / 1000);
    }

    public void thumbnailReady(ThumbnailKind kind, Duration elapsed) {
        registry.timer("sssok.thumbnail.ready.latency", "kind", tag(kind)).record(elapsed);
    }

    public void thumbnailRetried() { registry.counter("sssok.thumbnail.retried").increment(); }
    public void thumbnailFailed() { registry.counter("sssok.thumbnail.failed").increment(); }
    public void thumbnailRecovered() { registry.counter("sssok.thumbnail.recovered").increment(); }

    private Timer timer(ThumbnailKind kind, Source source, Result result) {
        return Timer.builder("sssok.media.processing")
            .description("One processing attempt; excludes executor queue and retry waiting")
            .tags("kind", tag(kind), "source", tag(source), "result", tag(result))
            .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20),
                Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2))
            .register(registry);
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
