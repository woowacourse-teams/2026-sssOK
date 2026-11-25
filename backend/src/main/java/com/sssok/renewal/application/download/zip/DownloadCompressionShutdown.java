package com.sssok.renewal.application.download.zip;

import com.sssok.infrastructure.config.TaskExecutorDrainer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 종료 대기 시간 안에 끝나지 못한 압축 잡을 실패로 닫는다(#842).
 *
 * <p>{@link TaskExecutorDrainer} 가 대기 상한까지 기다린 뒤에도 남은 잡은, 이후 빈 정리 단계의 {@code shutdownNow}
 * 가 대기열에서 버리거나 실행 중에 끊는다. 그대로 두면 QUEUED·RUNNING 으로 남아 stale 정리(30분)까지 사용자의
 * 동시 잡 한도를 차지하므로, DB 가 아직 열려 있는 이 단계에서 끊고 닫는다.
 *
 * <p>블루-그린 전환 중에는 다른 칸도 같은 DB 에서 압축을 돌리고 있어 "활성 잡 전부" 가 아니라 이 서버가 맡은
 * 잡만 닫는다.
 */
@Slf4j
@Component
class DownloadCompressionShutdown implements SmartLifecycle {

    // 중단된 압축이 멀티파트 업로드를 정리할 시간. 종료 시간 상한(웹 graceful 20s + 대기열 20s)에 더해진다.
    private static final Duration ABORT_WAIT = Duration.ofSeconds(3);

    private final DownloadCompressionDispatcher dispatcher;
    private final DownloadJobTransitions downloadJobTransitions;
    private final ThreadPoolTaskExecutor compressionExecutor;
    private volatile boolean running;

    DownloadCompressionShutdown(DownloadCompressionDispatcher dispatcher,
                                DownloadJobTransitions downloadJobTransitions,
                                @Qualifier("downloadCompressionTaskExecutor") AsyncTaskExecutor compressionExecutor) {
        this.dispatcher = dispatcher;
        this.downloadJobTransitions = downloadJobTransitions;
        this.compressionExecutor = (ThreadPoolTaskExecutor) compressionExecutor;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        Set<Long> leftover = dispatcher.inFlightJobIds();
        if (leftover.isEmpty()) {
            return;
        }
        // 워커를 먼저 끊는다. 실행 중인 워커가 진행률을 저장하면 행 전체를 덮어써, 먼저 닫아 둔 FAILED 가
        // RUNNING 으로 되돌아갈 수 있기 때문이다. 끊긴 워커는 업로드를 정리하고 스스로 실패를 기록한다.
        stopWorkers();
        // 대기열에서 버려진 잡과 끊기지 않은 잡만 남는다. 그사이 끝난 잡은 조건에서 빠진다.
        int closed = downloadJobTransitions.failActive(leftover, DownloadCompressionWorker.INTERRUPTED_REASON);
        log.warn("종료 대기 시간 안에 끝나지 못한 압축 잡을 정리했습니다. 남은 잡={}, 직접 닫은 잡={}",
            leftover.size(), closed);
    }

    // 대기열을 버리고 실행 중인 압축을 끊는다. 스토리지·DB 가 닫히기 전에 끝나도록 잠시 기다린다.
    private void stopWorkers() {
        ThreadPoolExecutor executor = compressionExecutor.getThreadPoolExecutor();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(ABORT_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("중단을 요청한 압축이 아직 끝나지 않았습니다. 실행 중={}", executor.getActiveCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // TaskExecutorDrainer 가 대기열을 비운 다음에 멈춘다(낮은 phase 가 나중에 멈춘다).
    @Override
    public int getPhase() {
        return TaskExecutorDrainer.PHASE - 1;
    }
}
