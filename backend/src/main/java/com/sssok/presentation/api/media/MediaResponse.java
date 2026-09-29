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
    @Schema(description = "워커가 만들기 전까지 null. R2 서명 URL이라 <img src>에 바로 쓸 수 있다")
    String thumbnailUrl,
    @Schema(description = "thumbnailUrl의 만료 시각. 지나면 목록을 다시 받아야 한다") Instant thumbnailUrlExpiresAt,
    @Schema(description = "뷰어용 서명 URL. 일반 사진은 preview, GIF·프리뷰 없는 사진·영상은 original")
    String displayUrl,
    @Schema(description = "displayUrl의 만료 시각. URL이 null이면 함께 null") Instant displayUrlExpiresAt,
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
            detail.mimeType(), detail.size(), detail.thumbnailUrl(), detail.thumbnailUrlExpiresAt(),
            detail.displayUrl(), detail.displayUrlExpiresAt(),
            detail.width(), detail.height(), detail.duration(), detail.folderIds(),
            detail.uploaderId(), detail.uploaderName(), detail.status(), detail.uploadedAt());
    }
}
