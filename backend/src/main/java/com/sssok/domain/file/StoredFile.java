package com.sssok.domain.file;

import java.time.Instant;

import com.sssok.domain.file.exception.FileSizeExceededException;
import com.sssok.domain.file.exception.IllegalUploadStatusException;
import com.sssok.domain.file.exception.UploadAlreadyCompletedException;
import com.sssok.domain.file.exception.UploadRetryExceededException;
import lombok.Getter;

@Getter
public class StoredFile {

    private final Long id;
    private final Long roomId;
    private final Long uploaderId;
    private final String originalFileName;
    private final MediaType mediaType;
    private FileSize fileSize;
    private final StorageKey storageKey;
    private final Instant createdAt;

    private Long folderId;
    private UploadStatus status;
    // 서명 URL 을 마지막으로 내준 시각. 고아 정리 배치(#77)가 이 값을 기준으로 회수한다.
    // createdAt 을 쓰면 재시도 중인 파일이 지워져서 따로 둔다.
    private Instant reservedAt;
    private int retryCount;

    // 썸네일 워커가 채운다. 그 전까지는 비어 있고, 영상은 끝까지 비어 있다.
    private StorageKey thumbnailKey;
    private Integer width;
    private Integer height;

    // 원본 EXIF 에서 읽는다. 카메라가 남기지 않았거나 편집 과정에서 지워졌으면 비어 있다.
    private Instant takenAt;
    private GeoPoint location;

    private StoredFile(Long id, Long roomId, Long uploaderId, String originalFileName,
                       MediaType mediaType, FileSize fileSize, StorageKey storageKey,
                       Long folderId, UploadStatus status, Instant createdAt,
                       Instant reservedAt, int retryCount,
                       StorageKey thumbnailKey, Integer width, Integer height,
                       Instant takenAt, GeoPoint location) {
        this.thumbnailKey = thumbnailKey;
        this.width = width;
        this.height = height;
        this.takenAt = takenAt;
        this.location = location;
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
                StorageKey.generate(roomId, mediaType), null, UploadStatus.RESERVED, now, now, 0,
                null, null, null, null, null);
    }

    public static StoredFile reconstruct(Long id, Long roomId, Long uploaderId,
                                         String originalFileName, MediaType mediaType,
                                         FileSize fileSize, StorageKey storageKey, Long folderId,
                                         UploadStatus status, Instant createdAt,
                                         Instant reservedAt, int retryCount,
                                         StorageKey thumbnailKey, Integer width, Integer height,
                                         Instant takenAt, GeoPoint location) {
        return new StoredFile(id, roomId, uploaderId, originalFileName, mediaType, fileSize,
                storageKey, folderId, status, createdAt, reservedAt, retryCount,
                thumbnailKey, width, height, takenAt, location);
    }

    private static void validateSize(MediaType mediaType, FileSize fileSize) {
        if (fileSize.exceeds(mediaType.maxBytes())) {
            throw new FileSizeExceededException(mediaType, fileSize);
        }
    }

    public void startProcessing() {
        transitionTo(UploadStatus.PROCESSING);
    }

    // 이미 올라간 파일을 덮어쓰지 못하게 RESERVED·FAILED 에서만 허용한다.
    // 정리 배치 기준 시각을 지금으로 미뤄, 재시도 중인 파일이 회수되지 않게 한다.
    public void reissueUploadUrl(int maxRetryCount, Instant now) {
        if (!status.canReissueUploadUrl()) {
            throw new UploadAlreadyCompletedException();
        }
        if (retryCount >= maxRetryCount) {
            throw new UploadRetryExceededException(maxRetryCount);
        }
        retryCount++;
        reservedAt = now;
    }

    // 재압축해서 다시 올리는 경우에만 크기가 바뀐다.
    public void changeFileSize(FileSize newFileSize) {
        validateSize(mediaType, newFileSize);
        this.fileSize = newFileSize;
    }

    // 스토리지에 실제로 올라온 것이 발급 때 신고한 값과 같은지 확인한다.
    public boolean matchesUploaded(long uploadedBytes, String uploadedMimeType) {
        return fileSize.bytes() == uploadedBytes && mediaType.contentType().equals(uploadedMimeType);
    }

    public void markReady() {
        transitionTo(UploadStatus.READY);
    }

    // 썸네일 워커가 일을 마쳤을 때. 결과를 담는 것과 상태를 넘기는 것이 항상 같이 일어나야
    // 하므로 한 메서드로 묶는다 — 따로 두면 값만 채우고 PROCESSING 에 남는 경우가 생긴다.
    public void completeProcessing(ProcessedMedia processed) {
        this.thumbnailKey = processed.thumbnailKey();
        this.width = processed.width();
        this.height = processed.height();
        this.takenAt = processed.takenAt();
        this.location = processed.location();
        transitionTo(UploadStatus.READY);
    }

    // 영상은 썸네일을 뽑지 못한다. 그렇다고 PROCESSING 에 두면 다운로드가 영영 409 로 막히므로
    // 썸네일 없이 완료로 넘긴다.
    public boolean canGenerateThumbnail() {
        return mediaType.isImage();
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
