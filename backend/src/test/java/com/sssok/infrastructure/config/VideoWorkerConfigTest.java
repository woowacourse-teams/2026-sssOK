package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class VideoWorkerConfigTest {

    @Test
    void 영상_썸네일용_독립_스레드_풀을_만든다() {
        VideoWorkerProperties properties = new VideoWorkerProperties(2, 3, 50);

        Executor configured = new VideoWorkerConfig().videoThumbnailTaskExecutor(properties);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(3);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("video-thumbnail-");
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(50);
        executor.shutdown();
    }

    @Test
    void 기존_공용_스레드_풀도_함께_등록한다() {
        Executor configured = new VideoWorkerConfig()
            .applicationTaskExecutor("media-worker-", 4, 8, 100);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("media-worker-");
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
        executor.shutdown();
    }
}
