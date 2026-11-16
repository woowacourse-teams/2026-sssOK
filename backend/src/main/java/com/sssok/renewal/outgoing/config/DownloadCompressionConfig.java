package com.sssok.renewal.outgoing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DownloadCompressionConfig {

    @Bean(name = "downloadCompressionTaskExecutor")
    public AsyncTaskExecutor downloadCompressionTaskExecutor(DownloadCompressionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("media-worker-zip-");
        executor.setCorePoolSize(properties.workers());
        executor.setMaxPoolSize(properties.workers());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.initialize();
        return executor;
    }
}
