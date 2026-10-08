package com.sssok.common.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MediaProcessingMetrics {

    public enum Kind {
        IMAGE, VIDEO;

        public static Kind of(boolean video) {
            return video ? VIDEO : IMAGE;
        }
    }

    public enum Source { INITIAL, SWEEPER }

    public enum Result { SUCCESS, RETRYABLE, PERMANENT_FAILURE, COMPLETED_WITHOUT_THUMBNAIL, SKIPPED }

    private final MeterRegistry registry;
    private final AtomicInteger sweepFound = new AtomicInteger();
    private final AtomicLong sweepLastSuccess = new AtomicLong();

    public MediaProcessingMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Kind kind : Kind.values()) {
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
        Gauge.builder("sssok.media.sweep.found", sweepFound, AtomicInteger::get)
            .description("Media selected by the last sweep, bounded by sweep batch size").register(registry);
        Gauge.builder("sssok.media.sweep.last.success", sweepLastSuccess, AtomicLong::get)
            .baseUnit("seconds").description("Unix time of the last completed sweep").register(registry);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void completed(Timer.Sample sample, Kind kind, Source source, Result result) {
        sample.stop(timer(kind, source, result));
    }

    public void submitted(Kind kind, Source source, boolean accepted) {
        registry.counter("sssok.media.submissions", "kind", tag(kind), "source", tag(source),
            "result", accepted ? "accepted" : "rejected").increment();
    }

    public void swept(int found) {
        sweepFound.set(found);
        sweepLastSuccess.set(registry.config().clock().wallTime() / 1000);
    }

    private Timer timer(Kind kind, Source source, Result result) {
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
