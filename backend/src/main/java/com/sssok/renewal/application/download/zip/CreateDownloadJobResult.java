package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.domain.download.status.DownloadJobStatus;

public record CreateDownloadJobResult(
    Long jobId,
    DownloadJobStatus status,
    int mediaCount,
    long totalSize,
    String fileName
) {

    public static CreateDownloadJobResult from(DownloadJob job) {
        return new CreateDownloadJobResult(
            job.getId(), job.getStatus(), job.getMediaCount(), job.getTotalSizeBytes(), job.getFileName());
    }
}
