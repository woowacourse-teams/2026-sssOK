package com.sssok.presentation.api.download;

import com.sssok.application.download.BatchDownloadFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record BatchDownloadFileResponse(
    @Schema(description = "미디어 ID") Long mediaId,
    @Schema(description = "다운로드 파일명 (중복 시 자동으로 번호가 붙는다)") String fileName,
    @Schema(description = "스토리지 서명 다운로드 URL") String downloadUrl,
    @Schema(description = "서명 URL 만료 시각") Instant expiresAt
) {

    public static BatchDownloadFileResponse from(BatchDownloadFile file) {
        return new BatchDownloadFileResponse(file.mediaId(), file.fileName(), file.downloadUrl(), file.expiresAt());
    }
}
