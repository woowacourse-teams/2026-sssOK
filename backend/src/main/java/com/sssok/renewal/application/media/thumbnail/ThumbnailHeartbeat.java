package com.sssok.renewal.application.media.thumbnail;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailClaim;

import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// ScheduledExecutorService 자체를 빈으로 노출하면 Boot의 일반 TaskScheduler 자동 설정이
// 물러날 수 있다. 전용 객체 안에 두어 회수/정리 배치와 heartbeat 스레드를 분리한다.
@Slf4j
@Component("renewalThumbnailHeartbeat")
public class ThumbnailHeartbeat {
    private final ThumbnailJobService thumbnailJobService;
    private final long heartbeatIntervalMillis;

    public ThumbnailHeartbeat(ThumbnailJobService thumbnailJobService, ThumbnailQueueProperties properties) {
        this.thumbnailJobService = thumbnailJobService;
        this.heartbeatIntervalMillis = properties.heartbeatInterval().toMillis();
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "thumbnail-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public void executeWhileRenewingLease(ThumbnailClaim claimedJob, Runnable processing,
            Consumer<RuntimeException> failureHandler) {
        ScheduledFuture<?> heartbeatTask = null;
        Thread workerThread = Thread.currentThread();
        AtomicBoolean processingActive = new AtomicBoolean(true);
        try {
            heartbeatTask = schedule(
                () -> renewLeaseOrInterruptWorker(claimedJob, workerThread, processingActive),
                heartbeatIntervalMillis);
            processing.run();
        } catch (RuntimeException failure) {
            // 기존과 같이 실패 기록을 마친 뒤 heartbeat를 종료한다.
            failureHandler.accept(failure);
        } finally {
            synchronized (processingActive) {
                processingActive.set(false);
            }
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
            // 인터럽트를 지우지 않아 워커가 다음 작업을 가져오지 않게 한다.
        }
    }

    private void renewLeaseOrInterruptWorker(ThumbnailClaim claimedJob, Thread workerThread,
            AtomicBoolean processingActive) {
        try {
            if (thumbnailJobService.renewLease(claimedJob) != ThumbnailJobService.Heartbeat.LOST) {
                return;
            }
            // 종료와 같은 잠금으로 늦은 heartbeat가 재사용된 스레드를 중단하는 것을 막는다.
            synchronized (processingActive) {
                if (processingActive.get()) {
                    workerThread.interrupt();
                }
            }
        } catch (RuntimeException failure) {
            log.warn("썸네일 임대 갱신 실패 mediaId={} claimToken={}",
                claimedJob.mediaId(), claimedJob.token(), failure);
        }
    }

    public ScheduledFuture<?> schedule(Runnable pulse, long intervalMillis) {
        return executor.scheduleWithFixedDelay(pulse, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
