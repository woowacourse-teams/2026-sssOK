package com.sssok.infrastructure.persistence.download;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.domain.download.DownloadJob;
import com.sssok.domain.download.DownloadJobStatus;
import com.sssok.domain.file.StorageKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DownloadJobRepositoryAdapter implements DownloadJobRepository {

    private final DownloadJobJpaRepository jpaRepository;
    private final DownloadJobMediaJpaRepository jobMediaJpaRepository;

    @Override
    public DownloadJob save(DownloadJob job) {
        return toDomain(jpaRepository.save(toEntity(job)));
    }

    @Override
    public Optional<DownloadJob> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public long countByRequesterIdAndStatusIn(Long requesterId, List<DownloadJobStatus> statuses) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        return jpaRepository.countByRequesterIdAndStatusIn(requesterId, names);
    }

    @Override
    public void saveJobMedia(Long jobId, List<Long> mediaIds) {
        List<DownloadJobMediaJpaEntity> entities = mediaIds.stream()
            .map(mediaId -> new DownloadJobMediaJpaEntity(null, jobId, mediaId))
            .toList();
        jobMediaJpaRepository.saveAll(entities);
    }

    @Override
    public List<Long> findMediaIdsByJobId(Long jobId) {
        return jobMediaJpaRepository.findAllByDownloadJobId(jobId).stream()
            .map(DownloadJobMediaJpaEntity::getMediaId)
            .toList();
    }

    @Override
    public List<DownloadJob> findAllExpiredReady(Instant threshold) {
        return jpaRepository.findAllByStatusAndReadyAtBefore(DownloadJobStatus.READY.name(), threshold).stream()
            .map(this::toDomain)
            .toList();
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
            DownloadJobStatus.valueOf(entity.getStatus()),
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
