package com.sssok.presentation.api.media;

import com.sssok.application.media.MediaDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Schema(description = "방 미디어 공통 표현")
public record MediaResponse(
    Long mediaId,
    @Schema(description = "IMAGE 또는 VIDEO") String type,
    String fileName,
    String mimeType,
    long size,
    @Schema(description = "워커가 만들기 전까지 null. img 태그에서 바로 쓸 수 있도록 인증 토큰을 포함한다")
    String thumbnailUrl,
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
        return from(detail, null);
    }

    public static MediaResponse from(MediaDetail detail, String accessToken) {
        return new MediaResponse(detail.mediaId(), detail.type(), detail.fileName(),
            detail.mimeType(), detail.size(), withToken(detail.thumbnailUrl(), accessToken),
            detail.originalUrl(),
            detail.width(), detail.height(), detail.duration(), detail.folderIds(),
            detail.uploaderId(), detail.uploaderName(), detail.status(), detail.uploadedAt());
    }

    private static String withToken(String url, String accessToken) {
        if (url == null) {
            return null;
        }
        if (accessToken == null) {
            return url;
        }
        return url + "?token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
    }
}
