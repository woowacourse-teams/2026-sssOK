package com.sssok.common.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class DownloadProcessingMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger active = new AtomicInteger();

    public DownloadProcessingMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("sssok.download.active", active, AtomicInteger::get).register(registry);
        for (String result : new String[]{"success", "failure"}) {
            processing(result);
        }
        waiting();
    }

    public Timer.Sample start() {
        active.incrementAndGet();
        return Timer.start(registry);
    }

    public void startedAt(Instant createdAt) {
        Duration elapsed = Duration.between(createdAt, Instant.ofEpochMilli(registry.config().clock().wallTime()));
        if (!elapsed.isNegative()) {
            waiting().record(elapsed);
        }
    }

    public void completed(Timer.Sample sample, boolean success) {
        try {
            sample.stop(processing(success ? "success" : "failure"));
        } finally {
            active.decrementAndGet();
        }
    }

    private Timer processing(String result) {
        return timer("sssok.download.processing").tag("result", result).register(registry);
    }

    private Timer waiting() {
        return timer("sssok.download.waiting").register(registry);
    }

    private Timer.Builder timer(String name) {
        return Timer.builder(name).serviceLevelObjectives(Duration.ofMillis(100), Duration.ofSeconds(1),
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1),
            Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(30));
    }
}
