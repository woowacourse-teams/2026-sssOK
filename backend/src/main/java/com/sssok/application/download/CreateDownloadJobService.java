package com.sssok.application.download;

import com.sssok.application.download.exception.DownloadRateLimitedException;
import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.DownloadFileNames;
import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.config.DownloadProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class CreateDownloadJobService {

    private static final List<DownloadJobStatus> ACTIVE_STATUSES =
        List.of(DownloadJobStatus.QUEUED, DownloadJobStatus.RUNNING);

    private final DownloadTargetResolver downloadTargetResolver;
    private final DownloadJobRepository downloadJobRepository;
    private final DownloadProperties downloadProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CreateDownloadJobResult create(Long roomId, Long requesterId, List<Long> mediaIds, Long folderId) {
        long activeJobCount = downloadJobRepository.countByRequesterIdAndStatusIn(requesterId, ACTIVE_STATUSES);
        if (activeJobCount >= downloadProperties.maxConcurrentJobsPerRequester()) {
            throw new DownloadRateLimitedException();
        }

        List<StoredFile> targets = downloadTargetResolver.resolve(roomId, mediaIds, folderId);
        int mediaCount = targets.size();
        long totalSizeBytes = targets.stream().mapToLong(file -> file.getFileSize().bytes()).sum();
        String fileName = DownloadFileNames.zipNameOf(roomId);

        DownloadJob job = DownloadJob.create(roomId, requesterId, mediaCount, totalSizeBytes, fileName, Instant.now());
        DownloadJob saved = downloadJobRepository.save(job);
        downloadJobRepository.saveJobMedia(saved.getId(), targets.stream().map(StoredFile::getId).toList());
        eventPublisher.publishEvent(new DownloadJobRequestedEvent(saved.getId()));

        return CreateDownloadJobResult.from(saved);
    }
}
