package com.sssok.renewal.outgoing.processing.image;

import static org.assertj.core.api.Assertions.*;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.support.RecordingUploadTracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("native-image")
class LibvipsStageDiagnosticsTest extends LibvipsImageProcessorTest {
    @Override
    protected ThumbnailatorImageProcessor createProcessor() {
        return new ThumbnailatorImageProcessor(new ImageDerivativeOperations(),
            Optional.of(new LibvipsImageProcessor(null, true)));
    }

    @ParameterizedTest
    @CsvSource({"4032,3024", "4033,3025", "800,600", "100,80", "3024,4032"})
    void 진단_경로도_기존과_동일한_WebP를_만든다(int width, int height) throws Exception {
        byte[] jpeg = jpeg(width, height);
        var thumbnail = new DerivativeSpec(400, DerivativeFormat.WEBP, .8f);
        var preview = new DerivativeSpec(1600, DerivativeFormat.WEBP, .85f);
        var original = new LibvipsImageProcessor().derive(jpeg, thumbnail, preview);
        var diagnostic = new LibvipsImageProcessor(null, true).derive(jpeg, thumbnail, preview);
        assertThat(diagnostic.thumbnail().content()).isEqualTo(original.thumbnail().content());
        assertThat(diagnostic.preview().content()).isEqualTo(original.preview().content());
        // preview가 없으면 더 큰 JPEG shrink-on-load 계수가 선택된다.
        assertThat(new LibvipsImageProcessor(null, true).derive(jpeg, thumbnail, null).thumbnail().content())
            .isEqualTo(new LibvipsImageProcessor().derive(jpeg, thumbnail, null).thumbnail().content());
    }

    @ParameterizedTest
    @ValueSource(ints = {1,2,3,4,5,6,7,8})
    void 방향_보정과_홀수_치수도_출력이_같다(int orientation) throws Exception {
        byte[] source = jpeg(1603, 3201);
        var output = new ByteArrayOutputStream();
        output.write(source, 0, 2);
        output.write(ExifFixture.orientationApp1Segment(orientation));
        output.write(source, 2, source.length - 2);
        var thumb = new DerivativeSpec(400, DerivativeFormat.WEBP, .8f);
        var preview = new DerivativeSpec(1600, DerivativeFormat.WEBP, .85f);
        var baseline = new LibvipsImageProcessor().derive(output.toByteArray(), thumb, preview);
        var diagnostic = new LibvipsImageProcessor(null, true).derive(output.toByteArray(), thumb, preview);
        assertThat(diagnostic.thumbnail().content()).isEqualTo(baseline.thumbnail().content());
        assertThat(diagnostic.preview().content()).isEqualTo(baseline.preview().content());
    }

    @Test
    void 실제_연산_시간을_같은_derive_하위에_기록하고_일반_메트릭과_분리한다() throws Exception {
        try (var recording = new RecordingUploadTracing()) {
            var parent = recording.tracer.nextSpan().name("upload.processing.derive").start();
            try (var scope = recording.tracer.withSpan(parent)) {
                new LibvipsImageProcessor(recording.tracing, true).derive(jpeg(1601,1201),
                    new DerivativeSpec(400, DerivativeFormat.WEBP, .8f),
                    new DerivativeSpec(1600, DerivativeFormat.WEBP, .85f));
                assertThat(recording.tracer.currentSpan().context().spanId()).isEqualTo(parent.context().spanId());
            }
            parent.end();
            assertThat(recording.spans).hasSize(8);
            var root = recording.spans.getLast();
            long previousEnd = root.getStartEpochNanos();
            for (var span : recording.spans.subList(0, 7)) {
                assertThat(span.getTraceId()).isEqualTo(parent.context().traceId());
                assertThat(span.getParentSpanId()).isEqualTo(parent.context().spanId());
                assertThat(span.getStartEpochNanos()).isGreaterThanOrEqualTo(previousEnd);
                assertThat(span.getEndEpochNanos()).isBetween(span.getStartEpochNanos(), root.getEndEpochNanos());
                assertThat(span.getAttributes().get(AttributeKey.stringKey("image.execution")))
                    .isEqualTo("materialized_diagnostic");
                previousEnd = span.getEndEpochNanos();
            }
            assertThat(recording.registry.find("sssok.upload.processing.stage").timers()).isEmpty();
            assertThat(recording.registry.find("sssok.upload.native.diagnostic.stage").timers()).hasSize(7);
        }
    }

    @Test
    void 실패한_네이티브_단계도_종료하고_다음_호출에_문맥을_남기지_않는다() {
        try (var recording = new RecordingUploadTracing()) {
            assertThatThrownBy(() -> new LibvipsImageProcessor(recording.tracing, true).derive(
                new byte[] {(byte)255,(byte)216,(byte)255},
                new DerivativeSpec(400, DerivativeFormat.WEBP, .8f), null)).isInstanceOf(IllegalStateException.class);
            assertThat(recording.spans).hasSize(1);
            assertThat(recording.spans.getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
            assertThat(recording.tracer.currentSpan()).isNull();
        }
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.RED, width, height, java.awt.Color.BLUE));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
