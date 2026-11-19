package com.sssok.renewal.outgoing.storage;

import com.sssok.domain.file.StorageKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Timeout(10)
class ImageDerivativeUploaderTest {
    private final ThumbnailMediaStorage storage = mock(ThumbnailMediaStorage.class);
    private final StorageKey thumbnailKey = new StorageKey("rooms/1/thumbnails/a.webp");
    private final StorageKey previewKey = new StorageKey("rooms/1/previews/a.webp");
    private final byte[] thumbnail = {1};
    private final byte[] preview = {2};

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 두_업로드가_겹쳐_실행되고_썸네일_실패시에도_프리뷰가_끝나야_반환한다(boolean failThumbnail) throws Exception {
        CountDownLatch previewStarted = new CountDownLatch(1);
        CountDownLatch thumbnailFinished = new CountDownLatch(1);
        CountDownLatch releasePreview = new CountDownLatch(1);
        RuntimeException failure = new IllegalStateException("thumbnail PUT failed");
        doAnswer(call -> {
            previewStarted.countDown();
            assertThat(releasePreview.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(storage).uploadPreview(previewKey, preview);
        doAnswer(call -> {
            assertThat(previewStarted.await(5, TimeUnit.SECONDS)).isTrue();
            thumbnailFinished.countDown();
            if (failThumbnail) throw failure;
            return null;
        }).when(storage).uploadThumbnail(thumbnailKey, thumbnail);
        try (var uploadPool = Executors.newSingleThreadExecutor(); var caller = Executors.newSingleThreadExecutor()) {
            var uploader = new ImageDerivativeUploader(storage, uploadPool);
            var result = caller.submit(() -> uploader.upload(thumbnailKey, thumbnail, previewKey, preview));
            try {
                assertThat(thumbnailFinished.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(result.isDone()).isFalse();
            } finally {
                releasePreview.countDown();
            }
            if (failThumbnail) {
                assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCause(failure);
            } else {
                result.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void 프리뷰_스레드도_부모_트레이스를_잇고_다음_작업에_문맥을_남기지_않는다() throws Exception {
        try (var recording = new com.sssok.support.RecordingUploadTracing()) {
            var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory(
                java.util.Map.of("tracer", recording.tracer));
            var properties = new com.sssok.renewal.outgoing.config.ThumbnailQueueProperties(
                1, 1, 0, null, null, null, null, 0);
            var executor = new com.sssok.renewal.outgoing.config.ThumbnailQueueConfig()
                .imageDerivativeUploadTaskExecutor(properties,
                    beans.getBeanProvider(io.micrometer.tracing.Tracer.class));
            executor.initialize();
            try {
                var parent = recording.tracer.nextSpan().name("processing").start();
                doAnswer(call -> {
                    recording.tracing.stage(com.sssok.renewal.outgoing.monitoring.UploadTracing.Stage.UPLOAD_PREVIEW,
                        () -> null);
                    return null;
                }).when(storage).uploadPreview(previewKey, preview);
                try (var scope = recording.tracer.withSpan(parent)) {
                    new ImageDerivativeUploader(storage, executor)
                        .upload(thumbnailKey, thumbnail, previewKey, preview);
                } finally {
                    parent.end();
                }
                assertThat(recording.spans).hasSize(2).allSatisfy(span ->
                    assertThat(span.getTraceId()).isEqualTo(parent.context().traceId()));
                assertThat(recording.spans.stream().filter(span -> !span.getName().equals("processing")))
                    .allSatisfy(span -> assertThat(span.getParentSpanId()).isEqualTo(parent.context().spanId()));
                assertThat(executor.getThreadPoolExecutor().submit(recording.tracer::currentSpan)
                    .get(5, TimeUnit.SECONDS)).isNull();
            } finally {
                executor.shutdown();
            }
        }
    }

    @Test
    void 두_PUT이_모두_실패하면_두_원인을_보존한다() {
        var thumbnailFailure = new IllegalStateException("thumbnail");
        var previewFailure = new IllegalStateException("preview");
        doThrow(thumbnailFailure).when(storage).uploadThumbnail(thumbnailKey, thumbnail);
        doThrow(previewFailure).when(storage).uploadPreview(previewKey, preview);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var uploader = new ImageDerivativeUploader(storage, pool);
            assertThatThrownBy(() -> uploader.upload(thumbnailKey, thumbnail, previewKey, preview))
                .isSameAs(thumbnailFailure);
            assertThat(thumbnailFailure.getSuppressed()).containsExactly(previewFailure);
        }
    }

    @Test
    void 프리뷰만_실패해도_성공으로_반환하지_않는다() {
        var failure = new IllegalStateException("preview PUT failed");
        doThrow(failure).when(storage).uploadPreview(previewKey, preview);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var uploader = new ImageDerivativeUploader(storage, pool);
            assertThatThrownBy(() -> uploader.upload(thumbnailKey, thumbnail, previewKey, preview))
                .isSameAs(failure);
            verify(storage).uploadThumbnail(thumbnailKey, thumbnail);
        }
    }

    @Test
    void 실행기가_거절하면_순차_업로드로_완료한다() {
        var uploader = new ImageDerivativeUploader(storage, task -> { throw new RejectedExecutionException(); });
        uploader.upload(thumbnailKey, thumbnail, previewKey, preview);
        var order = inOrder(storage);
        order.verify(storage).uploadThumbnail(thumbnailKey, thumbnail);
        order.verify(storage).uploadPreview(previewKey, preview);
    }

    @Test
    void 프리뷰가_없으면_실행기를_사용하지_않는다() {
        var uploader = new ImageDerivativeUploader(storage, task -> { throw new AssertionError("unexpected task"); });
        uploader.upload(thumbnailKey, thumbnail, null, null);
        verify(storage).uploadThumbnail(thumbnailKey, thumbnail);
        verifyNoMoreInteractions(storage);
    }
}
