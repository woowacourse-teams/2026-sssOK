package com.sssok.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import com.sssok.application.media.MediaEventListener;
import com.sssok.application.media.MediaReadyEvent;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.application.search.AnalyzeImageService;
import com.sssok.infrastructure.scheduler.ImageAnalysisSweeper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "media.search.enabled=true", "media.search.provider=test", "media.search.created-since=2026-01-01T00:00:00Z",
    "media.search.sweep-delay=3600000", "media.thumbnail.auto-generate=false"
})
class ImageAnalysisWorkflowTest extends PostgresContainerSupport {
    private static final long MEDIA_ID = 460101L;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyzeImageService analyzer;
    @Autowired MediaSearchDocumentRepository documents;
    @Autowired ImageAnalysisSweeper sweeper;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean ImageDescriptionPort descriptions;
    @MockitoBean TextEmbeddingPort embeddings;
    @MockitoBean FileStoragePort storage;
    @MockitoBean MediaEventListener mediaEvents;
    @MockitoBean(name = "imageAnalysisTaskExecutor") AsyncTaskExecutor executor;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("""
            INSERT INTO stored_file (id, room_id, uploader_id, original_file_name,
                media_type, file_size_bytes, storage_key, status, created_at, updated_at, reserved_at,
                preview_key)
            VALUES (?, 1, 1, 'photo.jpg', 'JPEG', 100, 'test/source.jpg', 'READY',
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test/preview.webp')
            """, MEDIA_ID);
        given(storage.presignGet(any(), anyString(), anyString(), any())).willReturn("https://signed.test/image");
        given(descriptions.describe(anyString(), any())).willReturn(
            new ImageDescriptionPort.Description("해변 단체 사진", "바다, 사람", "vision-test", "v1"));
        given(embeddings.embed(anyString(), any())).willReturn(
            new TextEmbeddingPort.Embedding(List.of(1.0, 0.0, 0.0), "embedding-test"));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stored_file WHERE id = ?", MEDIA_ID);
    }

    @Test
    void 커밋_후_별도_트랜잭션에서_등록하고_제출한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            events.publishEvent(new MediaReadyEvent(1L, MEDIA_ID)));
        assertThat(status()).isEqualTo("PENDING");
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void 롤백된_이벤트는_작업을_등록하거나_제출하지_않는다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new MediaReadyEvent(1L, MEDIA_ID));
            status.setRollbackOnly();
        });
        assertThat(count()).isZero();
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void 제출_거절_후에도_대기_작업을_유지한다() {
        doThrow(new TaskRejectedException("full")).when(executor).execute(any(Runnable.class));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            events.publishEvent(new MediaReadyEvent(1L, MEDIA_ID)));
        assertThat(status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM media_search_document WHERE media_id = ?",
            Integer.class, MEDIA_ID)).isZero();
    }

    @Test
    void 프리뷰_설명과_임베딩을_저장하고_중복_실행은_AI를_호출하지_않는다() {
        documents.register(MEDIA_ID);
        analyzer.analyze(MEDIA_ID);
        analyzer.analyze(MEDIA_ID);
        assertThat(status()).isEqualTo("READY");
        verify(descriptions).describe(anyString(), any());
        verify(embeddings).embed(org.mockito.ArgumentMatchers.eq("해변 단체 사진\n바다, 사람"), any());
        verify(storage).presignGet(org.mockito.ArgumentMatchers.eq(
            new com.sssok.domain.file.StorageKey("test/preview.webp")),
            org.mockito.ArgumentMatchers.eq("inline"), org.mockito.ArgumentMatchers.eq("image/webp"), any());
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM media_search_document WHERE media_id = ?",
            Integer.class, MEDIA_ID)).isEqualTo(3);
    }

    @Test
    void AI_실패는_재시도_대기로_남고_업로드_상태는_READY다() {
        documents.register(MEDIA_ID);
        given(descriptions.describe(anyString(), any())).willThrow(new IllegalStateException("timeout"));
        analyzer.analyze(MEDIA_ID);
        assertThat(status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM stored_file WHERE id = ?", String.class, MEDIA_ID))
            .isEqualTo("READY");
        verify(embeddings, never()).embed(anyString(), any());
        assertThat(documents.claim(MEDIA_ID, Instant.now(), 3)).isEmpty();
    }

    @Test
    void 분석_도중_삭제되면_결과를_남기지_않는다() {
        documents.register(MEDIA_ID);
        given(descriptions.describe(anyString(), any())).willAnswer(call -> {
            jdbc.update("DELETE FROM stored_file WHERE id = ?", MEDIA_ID);
            return new ImageDescriptionPort.Description("사진", "바다", "vision-test", "v1");
        });
        analyzer.analyze(MEDIA_ID);
        assertThat(count()).isZero();
    }

    @Test
    void 유실된_등록을_복구하고_중단된_작업을_다시_제출한다() {
        sweeper.sweep();
        assertThat(status()).isEqualTo("PENDING");
        documents.claim(MEDIA_ID, Instant.now().plusSeconds(1), 3).orElseThrow();
        jdbc.update("UPDATE media_search_document SET started_at = ? WHERE media_id = ?",
            java.sql.Timestamp.from(Instant.now().minusSeconds(600)), MEDIA_ID);
        sweeper.sweep();
        assertThat(status()).isEqualTo("PENDING");
        verify(executor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void 도입_시각_이전_사진은_이벤트와_복구_배치에서_제외한다() {
        jdbc.update("UPDATE stored_file SET created_at = TIMESTAMPTZ '2025-01-01 00:00:00Z' WHERE id = ?", MEDIA_ID);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            events.publishEvent(new MediaReadyEvent(1L, MEDIA_ID)));
        sweeper.sweep();
        assertThat(count()).isZero();
        analyzer.analyze(MEDIA_ID);
        verify(descriptions, never()).describe(anyString(), any());
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM media_search_document WHERE media_id = ?", String.class, MEDIA_ID);
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM media_search_document WHERE media_id = ?", Integer.class, MEDIA_ID);
    }
}
