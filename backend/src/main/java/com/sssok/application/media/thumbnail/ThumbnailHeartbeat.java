package com.sssok.application.media.thumbnail;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

// ScheduledExecutorService 자체를 빈으로 노출하면 Boot의 일반 TaskScheduler 자동 설정이
// 물러날 수 있다. 전용 객체 안에 두어 회수/정리 배치와 heartbeat 스레드를 분리한다.
@Component
public class ThumbnailHeartbeat {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "thumbnail-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ScheduledFuture<?> schedule(Runnable pulse, long intervalMillis) {
        return executor.scheduleWithFixedDelay(pulse, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
