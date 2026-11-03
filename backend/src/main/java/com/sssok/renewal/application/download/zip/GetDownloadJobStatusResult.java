package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.domain.download.status.DownloadJobStatus;
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
