package com.sssok.domain.file;

import java.time.Instant;

import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.IllegalUploadStatusException;
import lombok.Getter;

@Getter
public class StoredFile {

    private final Long id;
    private final Long roomId;
    private final Long uploaderId;
    private final String originalFileName;
    private final MediaType mediaType;
    private final FileSize fileSize;
    private final StorageKey storageKey;
    private final Instant createdAt;

    private Long folderId;
    private UploadStatus status;

    private StoredFile(Long id, Long roomId, Long uploaderId, String originalFileName,
                       MediaType mediaType, FileSize fileSize, StorageKey storageKey,
                       Long folderId, UploadStatus status, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.uploaderId = uploaderId;
        this.originalFileName = originalFileName;
        this.mediaType = mediaType;
        this.fileSize = fileSize;
        this.storageKey = storageKey;
        this.folderId = folderId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static StoredFile beginUpload(Long roomId, Long uploaderId, String originalFileName,
                                         FileSize fileSize, Long folderId, Instant now) {
        MediaType mediaType = MediaType.fromFileName(originalFileName);
        validateSize(mediaType, fileSize);

        return new StoredFile(null, roomId, uploaderId, originalFileName, mediaType, fileSize,
                StorageKey.generate(roomId, mediaType), folderId, UploadStatus.PENDING, now);
    }

    public static StoredFile reconstruct(Long id, Long roomId, Long uploaderId,
                                         String originalFileName, MediaType mediaType,
                                         FileSize fileSize, StorageKey storageKey, Long folderId,
                                         UploadStatus status, Instant createdAt) {
        return new StoredFile(id, roomId, uploaderId, originalFileName, mediaType, fileSize,
                storageKey, folderId, status, createdAt);
    }

    private static void validateSize(MediaType mediaType, FileSize fileSize) {
        if (fileSize.exceeds(mediaType.maxBytes())) {
            throw new FileSizeExceededException(mediaType, fileSize);
        }
    }

    public void startUploading() {
        transitionTo(UploadStatus.UPLOADING);
    }

    public void completeUpload() {
        transitionTo(UploadStatus.COMPLETED);
    }

    public void failUpload() {
        transitionTo(UploadStatus.FAILED);
    }

    public void retryUpload() {
        if (!status.isRetryable()) {
            throw new IllegalUploadStatusException(status, UploadStatus.UPLOADING);
        }
        transitionTo(UploadStatus.UPLOADING);
    }

    private void transitionTo(UploadStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalUploadStatusException(status, next);
        }
        status = next;
    }

    public void moveToFolder(Long folderId) {
        this.folderId = folderId;
    }

    public void moveToRoot() {
        this.folderId = null;
    }

    public boolean isInRoot() {
        return folderId == null;
    }

    public boolean isUploadedBy(Long memberId) {
        return uploaderId.equals(memberId);
    }

    public OptimizationPlan optimizationPlan() {
        return MediaOptimizationStrategy.decide(mediaType, fileSize);
    }
}
