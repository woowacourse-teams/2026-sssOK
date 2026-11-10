package com.sssok.renewal.outgoing.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Nested stages overlap: registration includes transaction completion and synchronous listeners. */
@Slf4j
@Component
public class UploadRegistrationMetrics {
    public enum Stage {
        TOTAL, ACCESS, VALIDATE, FILE_LOOKUP, STORAGE_HEAD, NICKNAME,
        REGISTRATION, FOLDERS, FILE_WRITE, RESPONSE, CREATED_PUBLISH, UPLOADED_PUBLISH
    }

    private final MeterRegistry registry;
    private final Map<Stage, Map<String, Timer>> timers = new EnumMap<>(Stage.class);

    public UploadRegistrationMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (Stage stage : Stage.values()) {
            Map<String, Timer> outcomes = new HashMap<>();
            for (String outcome : new String[]{"success", "partial", "error"}) {
                outcomes.put(outcome, Timer.builder("sssok.upload.registration.duration")
                    .tags("stage", stage.name().toLowerCase(Locale.ROOT), "outcome", outcome)
                    .register(registry));
            }
            timers.put(stage, outcomes);
        }
    }

    public <T> T measure(Stage stage, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "error";
        try {
            T value = action.get();
            outcome = "success";
            return value;
        } finally {
            sample.stop(timers.get(stage).get(outcome));
        }
    }

    public void measure(Stage stage, Runnable action) {
        measure(stage, () -> { action.run(); return null; });
    }

    public <T> T total(int requested, Supplier<T> action, ToIntFunction<T> rejected) {
        Timer.Sample sample = Timer.start(registry);
        long start = System.nanoTime();
        String outcome = "error";
        int failed = -1;
        try {
            T value = action.get();
            failed = rejected.applyAsInt(value);
            outcome = failed == 0 ? "success" : "partial";
            return value;
        } finally {
            sample.stop(timers.get(Stage.TOTAL).get(outcome));
            // Request IDs stay in the existing logging context, never metric labels.
            log.debug("upload_registration requested={} rejected={} outcome={} durationMs={}",
                requested, failed, outcome, (System.nanoTime() - start) / 1_000_000.0);
        }
    }
}
