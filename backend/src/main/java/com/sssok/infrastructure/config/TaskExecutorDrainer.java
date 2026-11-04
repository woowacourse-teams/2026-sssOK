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
 * 앱이 꺼질 때 비동기 작업 풀의 대기열까지 비우고 나서 다음 종료 단계로 넘긴다.
 *
 * <p>Spring 기본 동작은 실행 중인 작업만 기다리고 대기열은 버린다. 실행기마다
 * {@code waitForTasksToCompleteOnShutdown} 을 켜면 대기가 빈 정리 단계에서 일어나, DB 커넥션 풀이나
 * R2 클라이언트가 먼저 닫힌 뒤에 작업이 돌 수 있다. 그래서 빈 정리 전인 생명주기 종료 단계에서 비운다.
 *
 * <p>docker 의 stop_grace_period 는 웹 graceful 대기(spring.lifecycle.timeout-per-shutdown-phase)와
 * {@code shutdown.task-drain-timeout} 을 합친 것보다 길어야 한다.
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

    // 선언 타입이 AsyncTaskExecutor 인 빈도 있어, 모든 싱글턴이 만들어진 종료 시점에 실제 타입으로 찾는다.
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
