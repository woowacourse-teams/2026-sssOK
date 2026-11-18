package com.sssok.renewal.outgoing.monitoring;

import com.sssok.renewal.outgoing.config.VideoProperties;
import com.sssok.renewal.outgoing.processing.video.FfmpegVideoFrameExtractor;
import com.sssok.support.RecordingUploadTracing;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import static org.assertj.core.api.Assertions.*;

class UploadTracingAspectTest {
    @Test
    void 기존_등록_단계와_Runnable_호출을_누락이나_중복_없이_감싼다() {
        try (var recording = new RecordingUploadTracing()) {
            var metrics = proxy(new UploadRegistrationMetrics(recording.registry), recording);
            assertThat(metrics.total(1, () -> {
                for (var stage : UploadRegistrationMetrics.Stage.values()) {
                    if (stage != UploadRegistrationMetrics.Stage.TOTAL) {
                        metrics.measure(stage, () -> {});
                    }
                }
                return "response";
            }, result -> 0)).isEqualTo("response");
            assertThat(recording.spans).hasSize(UploadRegistrationMetrics.Stage.values().length);
            var total = recording.spans.getLast();
            for (var stage : UploadRegistrationMetrics.Stage.values()) {
                String name = stage.name().toLowerCase(Locale.ROOT);
                assertThat(recording.spans).filteredOn(span -> span.getName().equals("upload.registration." + name))
                    .singleElement().satisfies(span -> {
                        if (stage != UploadRegistrationMetrics.Stage.TOTAL) {
                            assertThat(span.getParentSpanId()).isEqualTo(total.getSpanId());
                        }
                    });
                assertThat(recording.registry.get("sssok.upload.registration.duration")
                    .tags("stage", name, "outcome", "success").timer().count()).isEqualTo(1);
            }
        }
    }

    @Test
    void 영상_추출_단계도_외부_계측으로_남긴다() {
        try (var recording = new RecordingUploadTracing()) {
            var extractor = proxy(new FfmpegVideoFrameExtractor(new VideoProperties(null, null, null, null)) {
                @Override
                public ExtractionResult extractFirstFrame(String sourceUrl, int maxWidth) {
                    return ExtractionResult.retryLater();
                }
            }, recording);
            assertThat(extractor.extractFirstFrame("https://private?secret=token", 400).retryable()).isTrue();
            assertThat(recording.spans).singleElement().satisfies(span -> {
                assertThat(span.getName()).isEqualTo("upload.processing.video_extract");
                assertThat(span.toString()).doesNotContain("private", "secret", "token");
            });
        }
    }

    @Test
    void 계측_프록시가_원래_실패를_변환하지_않는다() {
        try (var recording = new RecordingUploadTracing()) {
            var metrics = proxy(new UploadRegistrationMetrics(recording.registry), recording);
            var failure = new IllegalArgumentException("secret");
            assertThatThrownBy(() -> metrics.measure(UploadRegistrationMetrics.Stage.STORAGE_HEAD,
                (Runnable) () -> { throw failure; })).isSameAs(failure);
            assertThat(recording.tracer.currentSpan()).isNull();
            assertThat(recording.spans).hasSize(1);
            assertThat(recording.registry.get("sssok.upload.registration.duration")
                .tags("stage", "storage_head", "outcome", "error").timer().count()).isEqualTo(1);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, RecordingUploadTracing recording) {
        var factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new UploadTracingAspect(recording.tracing, new MediaProcessingMetrics(recording.registry)));
        return (T) factory.getProxy();
    }
}
