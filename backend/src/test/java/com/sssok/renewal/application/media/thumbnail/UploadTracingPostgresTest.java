package com.sssok.renewal.application.media.thumbnail;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.outgoing.database.ThumbnailJobJpaRepository;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Source;
import com.sssok.renewal.outgoing.monitoring.UploadTracing;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import com.sssok.support.RecordingUploadTracing;
import com.sssok.support.UploadRoomFixtures;
import io.micrometer.tracing.Span;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.sssok.support.UploadSizePolicyFixture.SIZE_POLICY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@PostgresIntegrationTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"
})
@Import(UploadTracingPostgresTest.TracingConfiguration.class)
class UploadTracingPostgresTest extends PostgresContainerSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired FileRepository files;
    @Autowired ThumbnailJobService jobs;
    @Autowired ThumbnailJobJpaRepository jobRows;
    @Autowired GenerateThumbnailService generator;
    @Autowired RecordingUploadTracing recording;
    @MockitoBean FileStorage storage;

    @Test
    void DB_등록_중복등록_실패_재시도_완료이벤트가_원래_요청에_연결된다() throws Exception {
        UploadRoomFixtures.ensure(jdbc, 839L);
        StoredFile file = StoredFile.reserve(839L, 839L, "private-name.jpg", "image/jpeg",
            new FileSize(1024), Instant.now(), SIZE_POLICY);
        file.startProcessing();
        file = files.save(file);
        Span request = recording.tracer.nextSpan().name("request").start();
        try (var scope = recording.tracer.withSpan(request)) {
            jobs.enqueueAll(List.of(file));
        } finally {
            request.end();
        }
        String savedParent = jobRows.findById(file.getId()).orElseThrow().getTraceparent();
        assertThat(savedParent).contains(request.context().traceId(), request.context().spanId());
        jobs.enqueueAll(List.of(file));
        assertThat(jobRows.findById(file.getId()).orElseThrow().getTraceparent()).isEqualTo(savedParent);
        when(storage.presignGet(any(), anyString(), anyString(), any())).thenReturn("https://storage.example.com/read");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        when(storage.openDownloadStream(any())).thenAnswer(ignored -> new ByteArrayInputStream(bytes.toByteArray()));
        doThrow(new IllegalStateException("private-presigned-url")).doNothing()
            .when(storage).upload(any(), any(byte[].class), anyString());
        try (var worker = Executors.newSingleThreadExecutor()) {
            var first = jobs.claimNext(ThumbnailKind.IMAGE).orElseThrow();
            worker.submit(() -> generator.generate(first, Source.INITIAL)).get(15, TimeUnit.SECONDS);
            assertThat(jobRows.findById(file.getId()).orElseThrow().getState()).isEqualTo("RETRY_WAIT");
            assertThat(jobRows.findById(file.getId()).orElseThrow().getTraceparent()).isEqualTo(savedParent);
            jdbc.update("UPDATE thumbnail_job SET available_at = CURRENT_TIMESTAMP WHERE media_id = ?", file.getId());
            var retry = jobs.claimNext(ThumbnailKind.IMAGE).orElseThrow();
            assertThat(retry.attempt()).isEqualTo(2);
            worker.submit(() -> generator.generate(retry, Source.SWEEPER)).get(15, TimeUnit.SECONDS);
            assertThat(worker.submit(recording.tracer::currentSpan).get()).isNull();
        }
        assertThat(files.findById(file.getId()).orElseThrow().getStatus()).isEqualTo(UploadStatus.READY);
        assertThat(recording.spans).allSatisfy(span -> assertThat(span.getTraceId()).isEqualTo(request.context().traceId()));
        assertThat(recording.spans).extracting(span -> span.getName()).contains(
            "upload.processing.download", "upload.processing.derive", "upload.processing.orientation",
            "upload.processing.decode", "upload.processing.resize", "upload.processing.metadata",
            "upload.processing.encode", "upload.processing.upload_thumbnail", "upload.processing.upload_preview",
            "upload.processing.complete", "upload.processing.ready_read", "upload.processing.ready_publish");
        var complete = recording.spans.stream().filter(span -> span.getName().equals("upload.processing.complete"))
            .findFirst().orElseThrow();
        assertThat(recording.spans).filteredOn(span -> span.getName().startsWith("upload.processing.ready_"))
            .hasSize(2).allSatisfy(span -> {
                assertThat(span.getParentSpanId()).isEqualTo(complete.getSpanId());
                assertThat(span.getStartEpochNanos()).isGreaterThanOrEqualTo(complete.getStartEpochNanos());
                assertThat(span.getEndEpochNanos()).isLessThanOrEqualTo(complete.getEndEpochNanos());
            });
        assertThat(recording.spans.toString()).doesNotContain("private-name", "private-presigned-url");
    }

    @TestConfiguration
    static class TracingConfiguration {
        @Bean(destroyMethod = "close")
        RecordingUploadTracing recording() { return new RecordingUploadTracing(); }
        @Bean
        @Primary
        UploadTracing testUploadTracing(RecordingUploadTracing recording) { return recording.tracing; }
    }
}
