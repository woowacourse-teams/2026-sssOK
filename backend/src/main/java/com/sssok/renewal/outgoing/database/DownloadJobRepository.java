package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.domain.download.status.DownloadJobStatus;
import com.sssok.renewal.domain.download.status.FailedDownloadJobStatus;
import com.sssok.renewal.domain.download.status.ReadyDownloadJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DownloadJobRepository {
    private final DownloadJobJpaRepository jpaRepository;
    private final DownloadJobMediaJpaRepository jobMediaJpaRepository;

    public DownloadJob save(DownloadJob job) {
        return toDomain(jpaRepository.save(toEntity(job)));
    }

    public Optional<DownloadJob> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    public long countByRequesterIdAndStatusIn(Long requesterId, List<DownloadJobStatus> statuses) {
        List<String> names = statuses.stream().map(DownloadJobStatus::name).toList();
        return jpaRepository.countByRequesterIdAndStatusIn(requesterId, names);
    }

    // 워커가 대상을 다시 해석하지 않도록 생성 시점의 대상을 고정한다.
    public void saveJobMedia(Long jobId, List<Long> mediaIds) {
        List<DownloadJobMediaJpaEntity> entities = mediaIds.stream()
            .map(mediaId -> new DownloadJobMediaJpaEntity(null, jobId, mediaId))
            .toList();
        jobMediaJpaRepository.saveAll(entities);
    }

    public List<Long> findMediaIdsByJobId(Long jobId) {
        return jobMediaJpaRepository.findAllByDownloadJobId(jobId).stream()
            .map(DownloadJobMediaJpaEntity::getMediaId)
            .toList();
    }

    public List<DownloadJob> findAllExpiredReady(Instant threshold) {
        String ready = ReadyDownloadJobStatus.INSTANCE.name();
        return jpaRepository.findAllByStatusAndReadyAtBefore(ready, threshold).stream()
            .map(this::toDomain)
            .toList();
    }

    public int failStaleActive(Instant threshold, String reason) {
        List<String> active = DownloadJobStatus.active().stream().map(DownloadJobStatus::name).toList();
        return jpaRepository.failStaleActive(active, FailedDownloadJobStatus.INSTANCE.name(), reason, threshold);
    }

    private DownloadJobJpaEntity toEntity(DownloadJob job) {
        return new DownloadJobJpaEntity(
            job.getId(),
            job.getRoomId(),
            job.getRequesterId(),
            job.getStatus().name(),
            job.getMediaCount(),
            job.getTotalSizeBytes(),
            job.getFileName(),
            job.getZipStorageKey() == null ? null : job.getZipStorageKey().value(),
            job.getProgress(),
            job.getReadyAt(),
            job.getFailureReason(),
            job.getCreatedAt()
        );
    }

    private DownloadJob toDomain(DownloadJobJpaEntity entity) {
        return DownloadJob.reconstruct(
            entity.getId(),
            entity.getRoomId(),
            entity.getRequesterId(),
            DownloadJobStatus.from(entity.getStatus()),
            entity.getMediaCount(),
            entity.getTotalSizeBytes(),
            entity.getFileName(),
            entity.getZipStorageKey() == null ? null : new StorageKey(entity.getZipStorageKey()),
            entity.getProgress(),
            entity.getCreatedAt(),
            entity.getReadyAt(),
            entity.getFailureReason()
        );
    }
}
