package com.sssok.presentation.api.download;

import com.sssok.application.download.GetDownloadJobStatusResult;
import com.sssok.domain.download.DownloadJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record GetDownloadJobStatusResponse(
    @Schema(description = "압축 작업 ID") Long jobId,
    @Schema(description = "QUEUED / RUNNING / READY / FAILED / EXPIRED") DownloadJobStatus status,
    @Schema(description = "진행률 0~100") int progress,
    @Schema(description = "압축 대상 개수") int mediaCount,
    @Schema(description = "zip 파일명") String fileName,
    @Schema(description = "완료 시 다운로드 서명 URL. READY가 아니면 null") String downloadUrl,
    @Schema(description = "다운로드 URL 만료 일시. READY가 아니면 null") Instant expiresAt,
    @Schema(description = "실패 사유. FAILED가 아니면 null") String failureReason
) {

    public static GetDownloadJobStatusResponse from(GetDownloadJobStatusResult result) {
        return new GetDownloadJobStatusResponse(
            result.jobId(), result.status(), result.progress(), result.mediaCount(),
            result.fileName(), result.downloadUrl(), result.expiresAt(), result.failureReason());
    }
}
