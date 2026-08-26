package com.sssok.presentation.api.media;

import com.sssok.application.media.CompleteUploadResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CompleteUploadResponse(
    @Schema(description = "등록에 성공한 미디어 목록") List<MediaResponse> registered,
    @Schema(description = "등록에 실패한 항목") List<FailedResponse> failed
) {
    public static CompleteUploadResponse from(CompleteUploadResult result) {
        return new CompleteUploadResponse(
            result.registered().stream().map(MediaResponse::from).toList(),
            result.failed().stream()
                .map(media -> new FailedResponse(media.mediaId(), media.code(), media.message()))
                .toList()
        );
    }

    public record FailedResponse(
        Long mediaId,
        @Schema(description = "UPLOAD_NOT_COMPLETED / FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE / MEDIA_NOT_FOUND")
        String code,
        String message
    ) {
    }
}
