package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.application.download.exception.DownloadExpiredException;
import com.sssok.renewal.application.download.exception.DownloadForbiddenException;
import com.sssok.renewal.application.download.exception.DownloadNotFoundException;
import com.sssok.renewal.outgoing.database.DownloadJobRepository;
import com.sssok.renewal.application.download.shared.DownloadUrlIssuer;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.outgoing.config.DownloadProperties;
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
    private final DownloadUrlIssuer downloadUrlIssuer;
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
        if (job.getStatus().isReady()) {
            expiresAt = job.getReadyAt().plus(downloadProperties.retention());
            downloadUrl = presignZipUrl(job, expiresAt);
        }

        return new GetDownloadJobStatusResult(job.getId(), job.getStatus(), job.getProgress(),
            job.getMediaCount(), job.getFileName(), downloadUrl, expiresAt, job.getFailureReason());
    }

    // URL 이 보관 기간보다 먼저 만료되지 않도록 조회할 때마다 남은 보관 시간만큼 서명한다.
    private String presignZipUrl(DownloadJob job, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return downloadUrlIssuer.issue(job.getZipStorageKey(), job.getFileName(), ZIP_CONTENT_TYPE, remaining);
    }
}
