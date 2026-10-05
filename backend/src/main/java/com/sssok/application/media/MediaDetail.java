package com.sssok.application.media;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sssok.domain.file.StoredFile;
import java.time.Instant;
import java.util.List;

// 미디어 목록 API 가 생기면 같은 형태를 쓰도록 응용 계층에 둔다.
// SSE payload 로 그대로 직렬화되므로, 보는 사람마다 다른 likedByMe 는 직렬화에서 뺀다.
public record MediaDetail(Long mediaId, String type, String fileName, String mimeType, long size,
                          String thumbnailUrl, Instant thumbnailUrlExpiresAt,
                          String displayUrl, Instant displayUrlExpiresAt,
                          Integer width, Integer height,
                          Integer duration, List<Long> folderIds, Long uploaderId,
                          String uploaderName, String status, Instant uploadedAt,
                          long likeCount, @JsonIgnore boolean likedByMe) {

    // 썸네일·프리뷰·크기·재생시간은 워커가 채우기 전까지 비어 있다. duration 은 사진이면 항상 비어 있다.
    public static MediaDetail of(StoredFile file, String uploaderName, List<Long> folderIds, MediaUrls urls,
                                 long likeCount, boolean likedByMe) {
        return new MediaDetail(
            file.getId(),
            file.getMediaType().isImage() ? "IMAGE" : "VIDEO",
            file.getOriginalFileName(),
            file.getMediaType().contentType(),
            file.getFileSize().bytes(),
            urls.thumbnailUrl(),
            urls.thumbnailUrlExpiresAt(),
            urls.displayUrl(),
            urls.displayUrlExpiresAt(),
            file.getWidth(),
            file.getHeight(),
            file.getDurationSeconds(),
            folderIds,
            file.getUploaderId(),
            uploaderName,
            file.getStatus().name(),
            file.getCreatedAt(),
            likeCount,
            likedByMe
        );
    }
}
