package com.sssok.presentation.api.download;

import com.sssok.application.download.CreateDownloadJobResult;
import com.sssok.domain.download.DownloadJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateDownloadJobResponse(
    @Schema(description = "압축 작업 ID") Long jobId,
    @Schema(description = "QUEUED / RUNNING / READY / FAILED / EXPIRED") DownloadJobStatus status,
    @Schema(description = "압축 대상 개수") int mediaCount,
    @Schema(description = "압축 대상 원본 총 바이트") long totalSize,
    @Schema(description = "생성될 zip 파일명") String fileName
) {

    public static CreateDownloadJobResponse from(CreateDownloadJobResult result) {
        return new CreateDownloadJobResponse(
            result.jobId(), result.status(), result.mediaCount(), result.totalSize(), result.fileName());
    }
}
