package com.sssok.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import com.sssok.common.monitoring.MediaProcessingMetrics.Source;
import com.sssok.common.monitoring.MediaProcessingMetrics.Result;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransferMetricsTest {

    @Test
    void 대시보드가_사용하는_Prometheus_이름과_초_단위_histogram을_노출한다() {
        MockClock clock = new MockClock();
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT, new PrometheusRegistry(), clock);
        try {
            MediaProcessingMetrics media = new MediaProcessingMetrics(registry);
            var sample = media.start();
            clock.add(Duration.ofSeconds(2));
            media.completed(sample, Kind.IMAGE, Source.SWEEPER, Result.SUCCESS);
            media.submitted(Kind.VIDEO, Source.INITIAL, false);
            media.swept(50);
            UploadMetrics upload = new UploadMetrics(registry);
            upload.registered(2, 1);
            DownloadProcessingMetrics download = new DownloadProcessingMetrics(registry);
            var zip = download.start();
            assertThat(registry.get("sssok.download.active").gauge().value()).isEqualTo(1);
            download.startedAt(Instant.ofEpochMilli(clock.wallTime() - 1000));
            clock.add(Duration.ofSeconds(5));
            download.completed(zip, true);

            String scrape = registry.scrape();
            assertThat(scrape).contains("sssok_media_processing_seconds_bucket",
                "sssok_media_processing_seconds_count", "sssok_media_submissions_total",
                "sssok_media_sweep_found 50.0", "sssok_media_sweep_last_success_seconds",
                "sssok_upload_files_total", "sssok_download_processing_seconds_bucket",
                "sssok_download_waiting_seconds_bucket", "sssok_download_active 0.0");
            assertThat(registry.get("sssok.media.processing")
                .tags("kind", "image", "source", "sweeper", "result", "success")
                .timer().totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(registry.get("sssok.upload.files")
                .tags("stage", "register", "result", "rejected").counter().count()).isEqualTo(1);
            assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                    assertThat(tag.getKey()).isIn("kind", "source", "result", "stage")));
        } finally {
            registry.close();
        }
    }
}
