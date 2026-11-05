package com.sssok.infrastructure.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 앱이 꺼질 때 비동기 작업 풀의 대기열까지 비운다.
 *
 * <p>Spring 기본 종료는 대기열을 버리고, {@code waitForTasksToCompleteOnShutdown} 은 DB·R2 가 닫히는 빈 정리
 * 단계에서 기다린다. 그래서 그보다 앞선 생명주기 종료 단계에서 비운다.
 *
 * <p>실행기 일시정지 단계의 추가 대기를 없애려고 {@code acceptTasksAfterContextClose} 를 켠다. 대기 상한을 넘긴
 * 작업은 빈 정리 단계의 {@code shutdownNow} 가 중단한다.
 */
@Slf4j
@Component
public class TaskExecutorDrainer implements SmartLifecycle {

    // 작업이 다른 풀로 후속 작업을 넘기므로(썸네일 → 스토리지 정리 → 알림) 받는 쪽을 뒤에 둔다.
    // 여기 없는 풀은 마지막에 비운다.
    static final List<String> DRAIN_ORDER = List.of(
        "imageThumbnailTaskExecutor",
        "videoThumbnailTaskExecutor",
        "imageAnalysisTaskExecutor",
        "applicationTaskExecutor",
        "roomNotificationTaskExecutor"
    );

    private final ApplicationContext applicationContext;
    private final Duration drainTimeout;
    private volatile boolean running;

    public TaskExecutorDrainer(ApplicationContext applicationContext,
                               @Value("${shutdown.task-drain-timeout:20s}") Duration drainTimeout) {
        this.applicationContext = applicationContext;
        this.drainTimeout = drainTimeout;
    }

    @Override
    public void start() {
        applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class, false, false)
            .values()
            .forEach(executor -> executor.setAcceptTasksAfterContextClose(true));
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        long deadline = System.nanoTime() + drainTimeout.toNanos();
        executorsInDrainOrder().forEach((name, executor) -> drain(name, executor, deadline));
    }

    private void drain(String name, ThreadPoolExecutor executor, long deadline) {
        int pending = pendingCount(executor);
        executor.shutdown();
        if (pending == 0) {
            return;
        }
        log.info("종료 전 비동기 작업을 마무리합니다. executor={}, 남은 작업={}", name, pending);
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                log.warn("종료 대기 시간 안에 비동기 작업을 끝내지 못했습니다. executor={}, 남은 작업={}",
                    name, pendingCount(executor));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int pendingCount(ThreadPoolExecutor executor) {
        return executor.getQueue().size() + executor.getActiveCount();
    }

    // 선언 타입이 AsyncTaskExecutor 인 빈도 있어 생성된 싱글턴을 실제 타입으로 찾는다.
    private Map<String, ThreadPoolExecutor> executorsInDrainOrder() {
        Map<String, ThreadPoolTaskExecutor> found =
            applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class, false, false);
        Stream<String> ordered = Stream.concat(
            DRAIN_ORDER.stream().filter(found::containsKey),
            found.keySet().stream().filter(name -> !DRAIN_ORDER.contains(name)));

        Map<String, ThreadPoolExecutor> executors = new LinkedHashMap<>();
        ordered.forEach(name -> {
            try {
                executors.put(name, found.get(name).getThreadPoolExecutor());
            } catch (IllegalStateException notInitialized) {
                log.debug("초기화되지 않은 실행기는 건너뜁니다. executor={}", name);
            }
        });
        return executors;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // 종료는 phase 가 높은 것부터 진행된다. 웹 graceful 종료 다음, 웹 서버 정지(그보다 1024 낮음)와
    // 실행기 일시정지(Integer.MAX_VALUE / 2)보다 먼저 멈춘다.
    @Override
    public int getPhase() {
        return WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 512;
    }
}
