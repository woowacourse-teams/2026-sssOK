package com.sssok.infrastructure.config;

import com.sssok.renewal.outgoing.config.DownloadCompressionConfig;
import com.sssok.renewal.outgoing.config.DownloadCompressionProperties;
import com.sssok.renewal.outgoing.config.ThumbnailQueueProperties;
import com.sssok.renewal.outgoing.config.ThumbnailQueueConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TaskExecutorConfigTest {

    @Test
    void 영상_썸네일용_독립_스레드_풀을_만든다() {
        ThumbnailQueueProperties properties = new ThumbnailQueueProperties(2, 2, 5, null, null, null, null, 50);

        Executor configured = new ThumbnailQueueConfig().videoThumbnailTaskExecutor(properties);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("video-thumbnail-");
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        executor.shutdown();
    }

    @Test
    void 기존_공용_스레드_풀도_함께_등록한다() {
        Executor configured = new ApplicationTaskExecutorConfig()
            .applicationTaskExecutor("media-worker-", 4, 8, 100);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("media-worker-");
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
        executor.shutdown();
    }

    @Test
    void 압축_전용_스레드_풀은_설정한_워커_수만큼_바로_동시에_돈다() {
        Executor configured = new DownloadCompressionConfig()
            .downloadCompressionTaskExecutor(new DownloadCompressionProperties(6, 50));

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configured;
        assertThat(executor.getCorePoolSize()).isEqualTo(6);
        assertThat(executor.getMaxPoolSize()).isEqualTo(6);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("media-worker-zip-");
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(50);
        executor.shutdown();
    }

    @Test
    void 압축_풀_설정이_없으면_기본값을_쓰고_대기열_0은_그대로_둔다() {
        DownloadCompressionProperties defaults = new DownloadCompressionProperties(null, null);
        DownloadCompressionProperties noQueue = new DownloadCompressionProperties(2, 0);

        assertThat(defaults.workers()).isEqualTo(8);
        assertThat(defaults.queueCapacity()).isEqualTo(100);
        assertThat(noQueue.queueCapacity()).isZero();
    }

    @Test
    void 압축_워커_수가_1보다_작으면_거부한다() {
        assertThatThrownBy(() -> new DownloadCompressionProperties(0, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DownloadCompressionProperties(1, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
