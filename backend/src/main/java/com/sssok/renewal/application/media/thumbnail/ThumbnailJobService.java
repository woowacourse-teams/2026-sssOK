package com.sssok.renewal.application.media.thumbnail;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.outgoing.database.ThumbnailJobRepository;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ThumbnailJobService {
    private final ThumbnailJobRepository jobRepository;
    private final UploadFileRepository fileRepository;
    private final ThumbnailQueueProperties properties;
    private final Clock clock;

    public ThumbnailJobService(ThumbnailJobRepository jobRepository, UploadFileRepository fileRepository,
                               ThumbnailQueueProperties properties, @Qualifier("thumbnailClock") Clock clock) {
        this.jobRepository = jobRepository;
        this.fileRepository = fileRepository;
        this.properties = properties;
        this.clock = clock;
    }

    // 등록 트랜잭션에서 파일 행을 잠근 뒤 호출한다. 기존 작업의 재시도 이력은 유지한다.
    @Transactional
    public void enqueue(StoredFile file) {
        boolean needsNewJob = jobRepository.findById(file.getId())
            .map(job -> job.getStatus().isFailed()).orElse(true);
        if (needsNewJob) {
            jobRepository.save(new ThumbnailJob(file.getId(), ThumbnailKind.of(file.getMediaType().isVideo()), clock.instant()));
        }
    }

    @Transactional
    public Optional<ThumbnailClaim> claimNext(ThumbnailKind kind) {
        Instant now = clock.instant();
        return jobRepository.claimable(kind, now).map(job -> {
            job.claim(now, now.plus(properties.lease()));
            jobRepository.save(job);
            log.info("썸네일 작업 선점 mediaId={} claimToken={} attempt={}",
                job.getMediaId(), job.getClaimToken(), job.getAttempts());
            return new ThumbnailClaim(job.getMediaId(), kind, job.getClaimToken(), job.getAttempts(), now);
        });
    }

    @Transactional
    public void returnRejected(ThumbnailClaim claim) {
        ThumbnailJob job = jobRepository.lock(claim.mediaId()).orElse(null);
        if (job != null && job.owns(claim.token(), clock.instant())) {
            job.rejected(clock.instant());
            jobRepository.save(job);
        }
    }

    public enum Heartbeat { RENEWED, LOST, FINISHED }

    @Transactional
    public Heartbeat renewLease(ThumbnailClaim claim) {
        Instant now = clock.instant();
        Instant deadline = claim.startedAt().plus(properties.maxRuntime());
        ThumbnailJob job = jobRepository.lock(claim.mediaId()).orElse(null);
        if (job == null) {
            return Heartbeat.LOST;
        }
        // 완료 커밋 후 이벤트 처리 중인 워커는 중단하지 않는다.
        if (!job.getStatus().isRunning()) {
            return Heartbeat.FINISHED;
        }
        if (!job.owns(claim.token(), now) || !now.isBefore(deadline)) {
            return Heartbeat.LOST;
        }
        Instant leaseUntil = now.plus(properties.lease());
        job.heartbeat(leaseUntil.isAfter(deadline) ? deadline : leaseUntil);
        jobRepository.save(job);
        return Heartbeat.RENEWED;
    }

    @Transactional
    public boolean recordGeneratedFiles(ThumbnailClaim claim, StorageKey thumbnailKey, StorageKey previewKey) {
        ThumbnailJob job = jobRepository.lock(claim.mediaId()).orElse(null);
        if (job == null || !job.owns(claim.token(), clock.instant())) {
            return false;
        }
        job.artifacts(thumbnailKey == null ? null : thumbnailKey.value(), previewKey == null ? null : previewKey.value());
        jobRepository.save(job);
        return true;
    }

    @Transactional
    public void enqueueMissing(Long mediaId) {
        fileRepository.findByIdForUpdate(mediaId).filter(file -> file.getStatus() == UploadStatus.PROCESSING)
            .ifPresent(this::enqueue);
    }

    public List<Long> findMissingMediaIds() {
        return jobRepository.missing(properties.recoveryBatchSize());
    }
}
