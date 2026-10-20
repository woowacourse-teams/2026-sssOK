package com.sssok.renewal.application.media.thumbnail;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics.Source;
import com.sssok.renewal.outgoing.monitoring.MediaProcessingMetrics;
import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ThumbnailDispatcher {
    private final ThumbnailJobService thumbnailJobService;
    private final ThumbnailJobCompletionService thumbnailJobCompletionService;
    private final GenerateThumbnailService generateThumbnailService;
    private final AsyncTaskExecutor imageThumbnailExecutor;
    private final AsyncTaskExecutor videoThumbnailExecutor;
    private final Semaphore availableImageWorkerSlots;
    private final Semaphore availableVideoWorkerSlots;
    private final ThumbnailHeartbeat thumbnailHeartbeat;
    private final MediaProcessingMetrics metrics;

    public ThumbnailDispatcher(
            ThumbnailJobService thumbnailJobService,
            ThumbnailJobCompletionService thumbnailJobCompletionService,
            GenerateThumbnailService generateThumbnailService,
            @Qualifier("imageThumbnailTaskExecutor") AsyncTaskExecutor imageThumbnailExecutor,
            @Qualifier("videoThumbnailTaskExecutor") AsyncTaskExecutor videoThumbnailExecutor,
            ThumbnailHeartbeat thumbnailHeartbeat,
            ThumbnailQueueProperties properties,
            MediaProcessingMetrics metrics
    ) {
        this.thumbnailJobService = thumbnailJobService;
        this.thumbnailJobCompletionService = thumbnailJobCompletionService;
        this.generateThumbnailService = generateThumbnailService;
        this.imageThumbnailExecutor = imageThumbnailExecutor;
        this.videoThumbnailExecutor = videoThumbnailExecutor;
        this.thumbnailHeartbeat = thumbnailHeartbeat;
        this.metrics = metrics;
        availableImageWorkerSlots = new Semaphore(properties.imageWorkers());
        availableVideoWorkerSlots = new Semaphore(properties.videoWorkers());
    }

    public void dispatchPendingJobs() {
        submitAvailableWorkers(ThumbnailKind.IMAGE, availableImageWorkerSlots, imageThumbnailExecutor);
        submitAvailableWorkers(ThumbnailKind.VIDEO, availableVideoWorkerSlots, videoThumbnailExecutor);
    }

    private void submitAvailableWorkers(ThumbnailKind kind, Semaphore workerSlots, AsyncTaskExecutor thumbnailExecutor) {
        // 시작 시점의 빈 슬롯까지만 고른다. 빠르게 끝나더라도 한 주기의 DB 조회량은 제한한다.
        int availableWorkerCount = workerSlots.availablePermits();
        for (int i = 0; i < availableWorkerCount && workerSlots.tryAcquire(); i++) {
            // 제출 전에는 이 메서드가, 제출 후에는 워커가 슬롯을 반환한다.
            boolean submittedToWorker = false;
            try {
                Optional<ThumbnailClaim> nextClaimedJob = thumbnailJobService.claimNext(kind);
                if (nextClaimedJob.isEmpty()) {
                    return;
                }
                ThumbnailClaim claimedJob = nextClaimedJob.get();
                Source source = processingSource(claimedJob);
                try {
                    thumbnailExecutor.execute(() -> processAvailableJobs(claimedJob, workerSlots));
                    submittedToWorker = true;
                    metrics.submitted(kind, source, true);
                } catch (TaskRejectedException rejected) {
                    thumbnailJobService.returnRejected(claimedJob);
                    metrics.submitted(kind, source, false);
                    log.warn("썸네일 제출 거절, 대기로 반환 mediaId={} claimToken={}", claimedJob.mediaId(), claimedJob.token());
                    return;
                }
            } finally {
                if (!submittedToWorker) {
                    workerSlots.release();
                }
            }
        }
    }

    // 하나의 슬롯/실행기 태스크가 실행 가능한 작업을 연속 처리한다. 빈 큐에서는 반환하므로
    // 유휴 DB polling은 스케줄러만 담당한다. 재제출하지 않아 queueCapacity=0의 인계 경합도 없다.
    private void processAvailableJobs(ThumbnailClaim firstClaimedJob, Semaphore workerSlots) {
        try {
            ThumbnailClaim claimedJob = firstClaimedJob;
            while (true) {
                generateWithHeartbeat(claimedJob, processingSource(claimedJob));
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                Optional<ThumbnailClaim> nextClaimedJob = thumbnailJobService.claimNext(firstClaimedJob.kind());
                if (nextClaimedJob.isEmpty()) {
                    return;
                }
                claimedJob = nextClaimedJob.get();
                metrics.submitted(claimedJob.kind(), processingSource(claimedJob), true);
            }
        } finally {
            // 라이브러리가 interrupt를 무시하더라도 실제 반환 전에는 슬롯을 재사용하지 않는다.
            workerSlots.release();
        }
    }

    private void generateWithHeartbeat(ThumbnailClaim claimedJob, Source source) {
        thumbnailHeartbeat.executeWhileRenewingLease(
            claimedJob,
            () -> generateThumbnailService.generate(claimedJob, source),
            failure -> {
                log.warn("썸네일 워커 실패 mediaId={} claimToken={}",
                    claimedJob.mediaId(), claimedJob.token(), failure);
                thumbnailJobCompletionService.fail(claimedJob, false, "WORKER_ERROR");
            });
    }

    private Source processingSource(ThumbnailClaim claimedJob) {
        return claimedJob.attempt() == 1 ? Source.INITIAL : Source.SWEEPER;
    }
}
