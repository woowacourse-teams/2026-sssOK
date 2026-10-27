package com.sssok.renewal.outgoing.config;

import java.time.Clock;
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
