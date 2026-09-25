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
    @Schema(description = "상세 조회에서만 채워지는 상세 모달용 파생본(최대 1600px WebP) 서명 URL. "
        + "목록 조회에서는 항상 null이고, 영상과 이 기능 이전에 올라온 사진도 null이라 그때는 originalUrl을 쓴다")
    String previewUrl,
    @Schema(description = "previewUrl의 만료 시각. 지나면 상세를 다시 받아야 한다") Instant previewUrlExpiresAt,
    @Schema(description = "영상 재생과, previewUrl이 없는 사진에만 채워진다. previewUrl이 있는 사진은 "
        + "원본을 노출하지 않으므로 null이며, 저장 목적의 다운로드는 다운로드 API를 쓴다. "
        + "RESERVED·FAILED면 실물이 없어 null이다")
    String originalUrl,
    @Schema(description = "originalUrl의 만료 시각. 지나면 목록을 다시 받아야 한다") Instant originalUrlExpiresAt,
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
            detail.previewUrl(), detail.previewUrlExpiresAt(),
            detail.originalUrl(), detail.originalUrlExpiresAt(),
            detail.width(), detail.height(), detail.duration(), detail.folderIds(),
            detail.uploaderId(), detail.uploaderName(), detail.status(), detail.uploadedAt());
    }
}
