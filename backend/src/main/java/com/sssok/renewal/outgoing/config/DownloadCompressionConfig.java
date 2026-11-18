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

    // 압축 한 건마다 다음 원본 GET 을 최대 prefetch-depth 개까지 겹쳐 연다(#666). 동시 압축 수가 워커 수를
    // 넘지 않으므로 워커 수 × depth 로 두고 대기열은 두지 않는다. 받지 못하면 그 파일부터는 기존처럼 직접 연다.
    @Bean(name = "downloadSourcePrefetchTaskExecutor")
    public AsyncTaskExecutor downloadSourcePrefetchTaskExecutor(DownloadCompressionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int size = properties.workers() * Math.max(1, properties.prefetchDepth());
        executor.setThreadNamePrefix("media-worker-zip-prefetch-");
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }
}
