package com.sssok.application.media;

import com.sssok.domain.file.StoredFile;
import java.time.Instant;
import java.util.List;

// 미디어 목록 API 가 생기면 같은 형태를 쓰도록 응용 계층에 둔다.
public record MediaDetail(Long mediaId, String type, String fileName, String mimeType, long size,
                          String thumbnailUrl, Instant thumbnailUrlExpiresAt,
                          String previewUrl, Instant previewUrlExpiresAt,
                          String originalUrl, Instant originalUrlExpiresAt,
                          Integer width, Integer height,
                          Integer duration, List<Long> folderIds, Long uploaderId,
                          String uploaderName, String status, Instant uploadedAt) {

    // 썸네일·프리뷰·크기·재생시간은 워커가 채우기 전까지 비어 있다. duration 은 사진이면 항상 비어 있다.
    // previewUrl·originalUrl 중 무엇이 채워지는지는 목록이냐 상세냐에 따라 다르다 (MediaUrlResolver).
    public static MediaDetail of(StoredFile file, String uploaderName, List<Long> folderIds, MediaUrls urls) {
        return new MediaDetail(
            file.getId(),
            file.getMediaType().isImage() ? "IMAGE" : "VIDEO",
            file.getOriginalFileName(),
            file.getMediaType().contentType(),
            file.getFileSize().bytes(),
            urls.thumbnailUrl(),
            urls.thumbnailUrlExpiresAt(),
            urls.previewUrl(),
            urls.previewUrlExpiresAt(),
            urls.originalUrl(),
            urls.originalUrlExpiresAt(),
            file.getWidth(),
            file.getHeight(),
            file.getDurationSeconds(),
            folderIds,
            file.getUploaderId(),
            uploaderName,
            file.getStatus().name(),
            file.getCreatedAt()
        );
    }
}
