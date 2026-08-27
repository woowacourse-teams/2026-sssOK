package com.sssok.application.download;

import com.sssok.domain.download.DownloadJobStatus;
import java.time.Instant;

public record GetDownloadJobStatusResult(
    Long jobId,
    DownloadJobStatus status,
    int progress,
    int mediaCount,
    String fileName,
    String downloadUrl,
    Instant expiresAt,
    String failureReason
) {
}
