package com.sssok.renewal.outgoing.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sssok.renewal.outgoing.monitoring.UploadRegistrationMetrics.Stage.*;

class UploadRegistrationMetricsTest {
    @Test
    void exceptionsAreRethrownAndCountedOnce() {
        var registry = new SimpleMeterRegistry();
        var metrics = new UploadRegistrationMetrics(registry);
        var failure = new IllegalStateException("failure");
        assertThatThrownBy(() -> metrics.total(1,
            () -> metrics.measure(STORAGE_HEAD, () -> { throw failure; }), value -> 0))
            .isSameAs(failure);
        assertThat(registry.get("sssok.upload.registration.duration")
            .tags("stage", "total", "outcome", "error").timer().count()).isEqualTo(1);
        assertThat(registry.get("sssok.upload.registration.duration")
            .tags("stage", "storage_head", "outcome", "error").timer().count()).isEqualTo(1);
    }

    @Test
    void partialResultsAreNotRecordedAsFullSuccess() {
        var registry = new SimpleMeterRegistry();
        var metrics = new UploadRegistrationMetrics(registry);
        assertThat(metrics.total(3, () -> 2, rejected -> rejected)).isEqualTo(2);
        assertThat(registry.get("sssok.upload.registration.duration")
            .tags("stage", "total", "outcome", "partial").timer().count()).isEqualTo(1);
    }
}
