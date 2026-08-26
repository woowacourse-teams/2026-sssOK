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
    // 서명 URL 을 마지막으로 내준 시각. 고아 정리 배치(#77)가 이 값을 기준으로 회수한다.
    // createdAt 을 쓰면 재시도 중인 파일이 지워져서 따로 둔다.
    private Instant reservedAt;
    private int retryCount;

    private StoredFile(Long id, Long roomId, Long uploaderId, String originalFileName,
                       MediaType mediaType, FileSize fileSize, StorageKey storageKey,
                       Long folderId, UploadStatus status, Instant createdAt,
                       Instant reservedAt, int retryCount) {
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
        this.reservedAt = reservedAt;
        this.retryCount = retryCount;
    }

    // 클라이언트가 보낸 MIME 으로 타입을 정한다. 파일명 확장자는 위조하기 쉬워 기준으로 쓰지 않는다.
    public static StoredFile reserve(Long roomId, Long uploaderId, String originalFileName,
                                     String mimeType, FileSize fileSize, Instant now) {
        MediaType mediaType = MediaType.fromMimeType(mimeType);
        validateSize(mediaType, fileSize);

        return new StoredFile(null, roomId, uploaderId, originalFileName, mediaType, fileSize,
                StorageKey.generate(roomId, mediaType), null, UploadStatus.RESERVED, now, now, 0);
    }

    public static StoredFile reconstruct(Long id, Long roomId, Long uploaderId,
                                         String originalFileName, MediaType mediaType,
                                         FileSize fileSize, StorageKey storageKey, Long folderId,
                                         UploadStatus status, Instant createdAt,
                                         Instant reservedAt, int retryCount) {
        return new StoredFile(id, roomId, uploaderId, originalFileName, mediaType, fileSize,
                storageKey, folderId, status, createdAt, reservedAt, retryCount);
    }

    private static void validateSize(MediaType mediaType, FileSize fileSize) {
        if (fileSize.exceeds(mediaType.maxBytes())) {
            throw new FileSizeExceededException(mediaType, fileSize);
        }
    }

    public void startProcessing() {
        transitionTo(UploadStatus.PROCESSING);
    }

    // 스토리지에 실제로 올라온 것이 발급 때 신고한 값과 같은지 확인한다.
    public boolean matchesUploaded(long uploadedBytes, String uploadedMimeType) {
        return fileSize.bytes() == uploadedBytes && mediaType.contentType().equals(uploadedMimeType);
    }

    public void markReady() {
        transitionTo(UploadStatus.READY);
    }

    public void failUpload() {
        transitionTo(UploadStatus.FAILED);
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
