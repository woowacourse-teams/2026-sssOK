package com.sssok.renewal.outgoing.storage;

import com.sssok.domain.file.StorageKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 두 PUT이 모두 끝나야 호출자가 완료 처리 또는 실패 파일 정리를 시작할 수 있다. */
@Component
public class ImageDerivativeUploader {
    private final ThumbnailMediaStorage storage;
    private final Executor executor;

    public ImageDerivativeUploader(ThumbnailMediaStorage storage,
            @Qualifier("imageDerivativeUploadTaskExecutor") Executor executor) {
        this.storage = storage;
        this.executor = executor;
    }

    public void upload(StorageKey thumbnailKey, byte[] thumbnail, StorageKey previewKey, byte[] preview) {
        if (previewKey == null) {
            storage.uploadThumbnail(thumbnailKey, thumbnail);
            return;
        }
        CompletableFuture<Void> previewUpload;
        try {
            previewUpload = CompletableFuture.runAsync(() -> storage.uploadPreview(previewKey, preview), executor);
        } catch (RejectedExecutionException rejected) {
            // 종료/포화 시 작업을 유실하거나 무한 대기하지 않고 기존 순차 경로로 실행한다.
            storage.uploadThumbnail(thumbnailKey, thumbnail);
            storage.uploadPreview(previewKey, preview);
            return;
        }
        Throwable failure = null;
        try {
            storage.uploadThumbnail(thumbnailKey, thumbnail);
        } catch (RuntimeException | Error thumbnailFailure) {
            failure = thumbnailFailure;
        }
        // cancel하지 않는다. PUT은 중단 요청 뒤에도 끝날 수 있어 먼저 정리하면 파일이 다시 남는다.
        try {
            previewUpload.join();
        } catch (CompletionException previewFailure) {
            Throwable cause = previewFailure.getCause();
            if (failure == null) {
                failure = cause;
            } else if (failure != cause) {
                failure.addSuppressed(cause);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
    }
}
