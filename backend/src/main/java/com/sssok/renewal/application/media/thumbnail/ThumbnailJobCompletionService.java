package com.sssok.renewal.application.media.thumbnail;

import com.sssok.application.media.MediaReadyEvent;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailRetryPolicy;
import com.sssok.renewal.domain.media.thumbnail.status.CancelledThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.FailedThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.SucceededThumbnailJobStatus;
import com.sssok.renewal.domain.media.thumbnail.status.ThumbnailJobStatus;
import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;
import com.sssok.renewal.outgoing.database.ThumbnailJobRepository;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 파일과 작업을 함께 잠글 때는 항상 파일 → 작업 순서를 유지한다.
@Slf4j
@Service
public class ThumbnailJobCompletionService {
    private final ThumbnailJobRepository jobRepository;
    private final UploadFileRepository fileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrphanObjectCollector orphanObjectCollector;
    private final ThumbnailRetryPolicy retryPolicy;
    private final int recoveryBatchSize;
    private final Clock clock;
    private final MediaProcessingMetrics metrics;

    public ThumbnailJobCompletionService(ThumbnailJobRepository jobRepository, UploadFileRepository fileRepository,
            ApplicationEventPublisher eventPublisher, OrphanObjectCollector orphanObjectCollector,
            ThumbnailQueueProperties properties, @Qualifier("thumbnailClock") Clock clock,
            MediaProcessingMetrics metrics) {
        this.jobRepository = jobRepository;
        this.fileRepository = fileRepository;
        this.eventPublisher = eventPublisher;
        this.orphanObjectCollector = orphanObjectCollector;
        this.retryPolicy = new ThumbnailRetryPolicy(properties.maxAttempts(), properties.retryBase(), properties.retryMax());
        this.recoveryBatchSize = properties.recoveryBatchSize();
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public boolean complete(ThumbnailClaim claim, ProcessedMedia processedMedia) {
        StoredFile lockedFile = fileRepository.findByIdForUpdate(claim.mediaId()).orElse(null);
        ThumbnailJob lockedJob = jobRepository.lock(claim.mediaId()).orElse(null);
        if (lockedJob == null || !lockedJob.owns(claim.token(), clock.instant())) {
            enqueueUnusedFilesForCleanup(lockedFile, processedMedia.thumbnailKey(), processedMedia.previewKey());
            return false;
        }
        if (lockedFile == null || lockedFile.getStatus() != UploadStatus.PROCESSING) {
            enqueueUnusedFilesForCleanup(lockedFile, processedMedia.thumbnailKey(), processedMedia.previewKey());
            lockedJob.complete(terminalState(lockedFile),
                clock.instant(), null);
            jobRepository.save(lockedJob);
            return false;
        }
        lockedFile.completeProcessing(processedMedia);
        fileRepository.save(lockedFile);
        eventPublisher.publishEvent(new MediaReadyEvent(lockedFile.getRoomId(), lockedFile.getId()));
        lockedJob.complete(SucceededThumbnailJobStatus.INSTANCE, clock.instant(), null);
        jobRepository.save(lockedJob);
        metrics.thumbnailReady(claim.kind(), Duration.between(lockedJob.getCreatedAt(), clock.instant()));
        log.info("썸네일 작업 완료 mediaId={} claimToken={} attempt={}", claim.mediaId(), claim.token(), claim.attempt());
        return true;
    }

    @Transactional
    public void fail(ThumbnailClaim claim, boolean permanentFailure, String errorCode) {
        StoredFile lockedFile = fileRepository.findByIdForUpdate(claim.mediaId()).orElse(null);
        ThumbnailJob lockedJob = jobRepository.lock(claim.mediaId()).orElse(null);
        if (lockedJob == null || !lockedJob.owns(claim.token(), clock.instant())) {
            return;
        }
        enqueueUnusedFilesForCleanup(lockedFile, toStorageKey(lockedJob.getThumbnailKey()), toStorageKey(lockedJob.getPreviewKey()));
        failOrRetry(lockedJob, lockedFile, permanentFailure, errorCode);
    }

    @Transactional
    public void completeSkipped(ThumbnailClaim claim) {
        StoredFile lockedFile = fileRepository.findByIdForUpdate(claim.mediaId()).orElse(null);
        ThumbnailJob lockedJob = jobRepository.lock(claim.mediaId()).orElse(null);
        if (lockedJob == null || !lockedJob.owns(claim.token(), clock.instant())) {
            return;
        }
        lockedJob.complete(terminalState(lockedFile), clock.instant(), null);
        jobRepository.save(lockedJob);
    }

    @Transactional
    public void recoverExpired(Long mediaId) {
        StoredFile lockedFile = fileRepository.findByIdForUpdate(mediaId).orElse(null);
        ThumbnailJob lockedJob = jobRepository.lock(mediaId).orElse(null);
        if (lockedJob == null || !lockedJob.getStatus().isRunning() || lockedJob.getLeaseUntil().isAfter(clock.instant())) {
            return;
        }
        enqueueUnusedFilesForCleanup(lockedFile, toStorageKey(lockedJob.getThumbnailKey()), toStorageKey(lockedJob.getPreviewKey()));
        failOrRetry(lockedJob, lockedFile, false, "LEASE_EXPIRED");
        metrics.thumbnailRecovered();
    }

    public List<Long> findExpiredMediaIds() {
        return jobRepository.expired(clock.instant(), recoveryBatchSize);
    }

    @Transactional
    public void enqueueUnusedFilesForCleanup(Long mediaId, StorageKey... keys) {
        StoredFile lockedFile = fileRepository.findByIdForUpdate(mediaId).orElse(null);
        enqueueUnusedFilesForCleanup(lockedFile, keys);
    }

    // 호출자가 확보한 파일 잠금을 재사용한다. 외부 호출만 public 메서드에서 잠금을 얻는다.
    private void enqueueUnusedFilesForCleanup(StoredFile lockedFile, StorageKey... keys) {
        // 완료 커밋 응답이 유실되어도 실제 채택된 파생본을 지우지 않는다.
        List<StorageKey> unusedKeys = Arrays.stream(keys).filter(Objects::nonNull)
            .filter(storageKey -> lockedFile == null || (!storageKey.equals(lockedFile.getThumbnailKey()) && !storageKey.equals(lockedFile.getPreviewKey())))
            .toList();
        if (!unusedKeys.isEmpty()) {
            orphanObjectCollector.enqueue(unusedKeys);
        }
    }

    private void failOrRetry(ThumbnailJob lockedJob, StoredFile lockedFile, boolean permanentFailure, String errorCode) {
        if (lockedFile == null || lockedFile.getStatus() != UploadStatus.PROCESSING) {
            lockedJob.complete(terminalState(lockedFile),
                clock.instant(), errorCode);
        } else if (retryPolicy.shouldStop(lockedJob.getAttempts(), permanentFailure)) {
            lockedFile.failUpload();
            fileRepository.save(lockedFile);
            lockedJob.complete(FailedThumbnailJobStatus.INSTANCE, clock.instant(), errorCode);
            metrics.thumbnailFailed();
        } else {
            lockedJob.retry(clock.instant().plus(retryPolicy.nextDelay(lockedJob.getAttempts())), errorCode);
            metrics.thumbnailRetried();
        }
        jobRepository.save(lockedJob);
        log.info("썸네일 작업 상태 변경 mediaId={} state={} attempt={} reason={}",
            lockedJob.getMediaId(), lockedJob.getStatus().name(), lockedJob.getAttempts(), errorCode);
    }

    private static StorageKey toStorageKey(String value) { return value == null ? null : new StorageKey(value); }

    private ThumbnailJobStatus terminalState(StoredFile lockedFile) {
        if (lockedFile != null && lockedFile.getStatus() == UploadStatus.READY) {
            return SucceededThumbnailJobStatus.INSTANCE;
        }
        return CancelledThumbnailJobStatus.INSTANCE;
    }
}
