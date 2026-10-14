package com.sssok.application.media.thumbnail;

import com.sssok.application.media.GenerateThumbnailService;
import com.sssok.common.monitoring.MediaProcessingMetrics;
import com.sssok.common.monitoring.MediaProcessingMetrics.Kind;
import com.sssok.common.monitoring.MediaProcessingMetrics.Source;
import com.sssok.infrastructure.config.ThumbnailQueueProperties;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ThumbnailDispatcher {
    private final ThumbnailJobs jobs;
    private final GenerateThumbnailService generator;
    private final AsyncTaskExecutor imageExecutor;
    private final AsyncTaskExecutor videoExecutor;
    private final Semaphore imageSlots;
    private final Semaphore videoSlots;
    private final ThumbnailHeartbeat heartbeats;
    private final long heartbeatMillis;
    private final MediaProcessingMetrics metrics;

    public ThumbnailDispatcher(ThumbnailJobs jobs, GenerateThumbnailService generator,
        @Qualifier("imageThumbnailTaskExecutor") AsyncTaskExecutor imageExecutor,
        @Qualifier("videoThumbnailTaskExecutor") AsyncTaskExecutor videoExecutor,
        ThumbnailHeartbeat heartbeats,
        ThumbnailQueueProperties properties, MediaProcessingMetrics metrics) {
        this.jobs = jobs;
        this.generator = generator;
        this.imageExecutor = imageExecutor;
        this.videoExecutor = videoExecutor;
        this.heartbeats = heartbeats;
        this.metrics = metrics;
        imageSlots = new Semaphore(properties.imageWorkers());
        videoSlots = new Semaphore(properties.videoWorkers());
        heartbeatMillis = properties.lease().toMillis() / 3;
    }

    public void dispatch() {
        dispatch(Kind.IMAGE, imageSlots, imageExecutor);
        dispatch(Kind.VIDEO, videoSlots, videoExecutor);
    }

    private void dispatch(Kind kind, Semaphore slots, AsyncTaskExecutor executor) {
        // 시작 시점의 빈 슬롯까지만 고른다. 빠르게 끝나더라도 한 주기의 DB 조회량은 제한한다.
        int budget = slots.availablePermits();
        for (int i = 0; i < budget && slots.tryAcquire(); i++) {
            boolean handedOff = false;
            try {
                Optional<ThumbnailClaim> found = jobs.claim(kind);
                if (found.isEmpty()) { return; }
                ThumbnailClaim claim = found.get();
                Source source = claim.attempt() == 1 ? Source.INITIAL : Source.SWEEPER;
                try {
                    executor.execute(() -> drain(claim, slots));
                    handedOff = true;
                    metrics.submitted(kind, source, true);
                } catch (TaskRejectedException rejected) {
                    jobs.rejected(claim);
                    metrics.submitted(kind, source, false);
                    log.warn("썸네일 제출 거절, 대기로 반환 mediaId={} claimToken={}", claim.mediaId(), claim.token());
                    return;
                }
            } finally {
                if (!handedOff) { slots.release(); }
            }
        }
    }

    // 하나의 슬롯/실행기 태스크가 실행 가능한 작업을 연속 처리한다. 빈 큐에서는 반환하므로
    // 유휴 DB polling은 스케줄러만 담당한다. 재제출하지 않아 queueCapacity=0의 인계 경합도 없다.
    private void drain(ThumbnailClaim first, Semaphore slots) {
        try {
            ThumbnailClaim claim = first;
            while (true) {
                run(claim, claim.attempt() == 1 ? Source.INITIAL : Source.SWEEPER);
                if (Thread.currentThread().isInterrupted()) { return; }
                Optional<ThumbnailClaim> next = jobs.claim(first.kind());
                if (next.isEmpty()) { return; }
                claim = next.get();
                metrics.submitted(claim.kind(), claim.attempt() == 1 ? Source.INITIAL : Source.SWEEPER, true);
            }
        } finally {
            // 라이브러리가 interrupt를 무시하더라도 실제 반환 전에는 슬롯을 재사용하지 않는다.
            slots.release();
        }
    }

    private void run(ThumbnailClaim claim, Source source) {
        ScheduledFuture<?> heartbeat = null;
        Thread worker = Thread.currentThread();
        java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        try {
            heartbeat = heartbeats.schedule(() -> {
                try {
                    if (jobs.heartbeat(claim) == ThumbnailJobs.Heartbeat.LOST) {
                        synchronized (running) {
                            if (running.get()) { worker.interrupt(); }
                        }
                    }
                } catch (RuntimeException failure) {
                    log.warn("썸네일 임대 갱신 실패 mediaId={} claimToken={}", claim.mediaId(), claim.token(), failure);
                }
            }, heartbeatMillis);
            generator.generate(claim, source);
        } catch (RuntimeException failure) {
            log.warn("썸네일 워커 실패 mediaId={} claimToken={}", claim.mediaId(), claim.token(), failure);
            jobs.failed(claim, false, "WORKER_ERROR");
        } finally {
            synchronized (running) { running.set(false); }
            if (heartbeat != null) { heartbeat.cancel(false); }
            // 종료/소유권 상실의 interrupt를 보존해 drain이 새 작업을 선점하지 않게 한다.
        }
    }
}
