package com.sssok.application.download;

import com.sssok.application.download.exception.DownloadExpiredException;
import com.sssok.application.download.exception.DownloadForbiddenException;
import com.sssok.application.download.exception.DownloadNotFoundException;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.DownloadFileNames;
import com.sssok.infrastructure.config.DownloadProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class GetDownloadJobStatusService {

    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final DownloadJobRepository downloadJobRepository;
    private final FileStoragePort fileStoragePort;
    private final DownloadProperties downloadProperties;

    public GetDownloadJobStatusResult getStatus(Long jobId, Long requesterId) {
        DownloadJob job = downloadJobRepository.findById(jobId)
            .orElseThrow(DownloadNotFoundException::new);

        if (!job.isRequestedBy(requesterId)) {
            throw new DownloadForbiddenException();
        }
        if (job.isExpired(downloadProperties.retention(), Instant.now())) {
            throw new DownloadExpiredException();
        }

        String downloadUrl = null;
        Instant expiresAt = null;
        if (job.getStatus() == DownloadJobStatus.READY) {
            expiresAt = job.getReadyAt().plus(downloadProperties.retention());
            downloadUrl = presignZipUrl(job, expiresAt);
        }

        return new GetDownloadJobStatusResult(job.getId(), job.getStatus(), job.getProgress(),
            job.getMediaCount(), job.getFileName(), downloadUrl, expiresAt, job.getFailureReason());
    }

    // 저장된 URL을 재사용하지 않고 조회할 때마다 다시 서명한다 — presign 자체의 유효기간이
    // 보관 기간(retention)보다 먼저 끝나버리는 상황을 막기 위해, 남은 보관 시간만큼만 서명한다.
    private String presignZipUrl(DownloadJob job, Instant expiresAt) {
        String contentDisposition = DownloadFileNames.contentDispositionOf(job.getFileName());
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return fileStoragePort.presignGet(job.getZipStorageKey(), contentDisposition, ZIP_CONTENT_TYPE, remaining);
    }
}
