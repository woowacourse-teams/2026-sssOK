package com.sssok.renewal.outgoing.config;

import java.time.Clock;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThumbnailQueueConfig {
    @Bean
    public Clock thumbnailClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "imageThumbnailTaskExecutor")
    public ThreadPoolTaskExecutor imageThumbnailTaskExecutor(ThumbnailQueueProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("image-thumbnail-");
        executor.setCorePoolSize(properties.imageWorkers());
        executor.setMaxPoolSize(properties.imageWorkers());
        executor.setQueueCapacity(0);
        return executor;
    }

    // 워커마다 프리뷰 PUT 하나만 분리한다. 썸네일 PUT은 기존 워커에서 실행한다.
    @Bean(name = "imageDerivativeUploadTaskExecutor")
    public ThreadPoolTaskExecutor imageDerivativeUploadTaskExecutor(
            ThumbnailQueueProperties properties, ObjectProvider<Tracer> tracerProvider) {
        Tracer tracer = tracerProvider.getIfAvailable(() -> Tracer.NOOP);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("image-derivative-upload-");
        executor.setCorePoolSize(properties.imageWorkers());
        executor.setMaxPoolSize(properties.imageWorkers());
        executor.setQueueCapacity(0);
        executor.setTaskDecorator(task -> {
            Span parent = tracer.currentSpan();
            return () -> {
                try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
                    task.run();
                }
            };
        });
        return executor;
    }

    @Bean(name = "videoThumbnailTaskExecutor")
    public AsyncTaskExecutor videoThumbnailTaskExecutor(ThumbnailQueueProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("video-thumbnail-");
        executor.setCorePoolSize(properties.videoWorkers());
        executor.setMaxPoolSize(properties.videoWorkers());
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }
}
