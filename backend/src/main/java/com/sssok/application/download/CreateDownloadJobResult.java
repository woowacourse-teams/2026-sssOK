package com.sssok.application.download;

import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;

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
