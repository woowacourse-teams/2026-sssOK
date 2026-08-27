package com.sssok.application.media;

import com.sssok.domain.file.StoredFile;
import java.time.Instant;
import java.util.List;

// 미디어 목록 API 가 생기면 같은 형태를 쓰도록 응용 계층에 둔다.
public record MediaDetail(Long mediaId, String type, String fileName, String mimeType, long size,
                          String thumbnailUrl, String originalUrl, Integer width, Integer height,
                          Integer duration, List<Long> folderIds, Long uploaderId,
                          String uploaderName, String status, Instant uploadedAt) {

    // 썸네일·크기·재생 시간은 워커가 채우기 전까지 비어 있다.
    public static MediaDetail of(StoredFile file, String uploaderName, List<Long> folderIds) {
        return new MediaDetail(
            file.getId(),
            file.getMediaType().isImage() ? "IMAGE" : "VIDEO",
            file.getOriginalFileName(),
            file.getMediaType().contentType(),
            file.getFileSize().bytes(),
            null,
            null,
            null,
            null,
            null,
            folderIds,
            file.getUploaderId(),
            uploaderName,
            file.getStatus().name(),
            file.getCreatedAt()
        );
    }
}
