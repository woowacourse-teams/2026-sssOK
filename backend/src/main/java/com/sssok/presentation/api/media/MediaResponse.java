package com.sssok.presentation.api.media;

import com.sssok.application.media.MediaDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "방 미디어 공통 표현")
public record MediaResponse(
    Long mediaId,
    @Schema(description = "IMAGE 또는 VIDEO") String type,
    String fileName,
    String mimeType,
    long size,
    @Schema(description = "워커가 만들기 전까지 null") String thumbnailUrl,
    @Schema(description = "워커가 만들기 전까지 null") String originalUrl,
    Integer width,
    Integer height,
    @Schema(description = "영상 재생 시간(초)") Integer duration,
    @Schema(description = "이 미디어가 담긴 폴더 목록") List<Long> folderIds,
    Long uploaderId,
    String uploaderName,
    @Schema(description = "RESERVED / PROCESSING / READY / FAILED") String status,
    Instant uploadedAt
) {
    public static MediaResponse from(MediaDetail detail) {
        return new MediaResponse(detail.mediaId(), detail.type(), detail.fileName(),
            detail.mimeType(), detail.size(), detail.thumbnailUrl(), detail.originalUrl(),
            detail.width(), detail.height(), detail.duration(), detail.folderIds(),
            detail.uploaderId(), detail.uploaderName(), detail.status(), detail.uploadedAt());
    }
}
