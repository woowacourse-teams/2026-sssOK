package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.domain.download.DownloadJob;
import com.sssok.renewal.domain.download.status.DownloadJobStatus;
import com.sssok.renewal.domain.download.status.FailedDownloadJobStatus;
import com.sssok.renewal.domain.download.status.ReadyDownloadJobStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DownloadJobRepository {
    private static final String INSERT_JOB_MEDIA = """
        INSERT INTO download_job_media (download_job_id, media_id, created_at, updated_at)
        VALUES (?, ?, ?, ?)
        """;

    private final DownloadJobJpaRepository jpaRepository;
    private final DownloadJobMediaJpaRepository jobMediaJpaRepository;
    private final JdbcTemplate jdbcTemplate;

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
    // IDENTITY 키라 JPA saveAll 은 행마다 insert 를 따로 보낸다. 대상이 최대 1000건이라
    // 요청자 행을 잠근 접수 트랜잭션이 대상 수만큼 길어지지 않도록 JDBC 배치 한 번으로 묶는다.
    public void saveJobMedia(Long jobId, List<Long> mediaIds) {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbcTemplate.batchUpdate(INSERT_JOB_MEDIA, mediaIds, mediaIds.size(), (statement, mediaId) -> {
            statement.setLong(1, jobId);
            statement.setLong(2, mediaId);
            statement.setObject(3, now);
            statement.setObject(4, now);
        });
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

    public int failActive(Collection<Long> jobIds, String reason) {
        if (jobIds.isEmpty()) {
            return 0;
        }
        List<String> active = DownloadJobStatus.active().stream().map(DownloadJobStatus::name).toList();
        return jpaRepository.failActiveByIds(jobIds, active, FailedDownloadJobStatus.INSTANCE.name(), reason);
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
