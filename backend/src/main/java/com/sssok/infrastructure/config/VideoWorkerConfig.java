package com.sssok.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(VideoWorkerProperties.class)
public class VideoWorkerConfig {

    // 사용자 정의 Executor가 하나라도 생기면 Spring Boot의 applicationTaskExecutor 자동 설정은
    // 물러난다. ZIP 압축·사진 썸네일이 계속 기존 공용 풀을 쓰도록 같은 설정값으로 명시 등록한다.
    @Bean(name = "applicationTaskExecutor")
    public AsyncTaskExecutor applicationTaskExecutor(
        @Value("${spring.task.execution.thread-name-prefix:media-worker-}") String threadNamePrefix,
        @Value("${spring.task.execution.pool.core-size:4}") int coreSize,
        @Value("${spring.task.execution.pool.max-size:8}") int maxSize,
        @Value("${spring.task.execution.pool.queue-capacity:100}") int queueCapacity
    ) {
        return taskExecutor(threadNamePrefix, coreSize, maxSize, queueCapacity);
    }

    // 반환 타입이 Executor 면 빈이 만들어지기 전까지 스프링이 실제 타입을 몰라, 제출 거부를
    // 직접 다루려고 AsyncTaskExecutor 로 주입받는 쪽이 생성 순서에 따라 실패한다.
    @Bean(name = "videoThumbnailTaskExecutor")
    public AsyncTaskExecutor videoThumbnailTaskExecutor(VideoWorkerProperties properties) {
        return taskExecutor("video-thumbnail-", properties.coreSize(), properties.maxSize(),
            properties.queueCapacity());
    }

    private AsyncTaskExecutor taskExecutor(String threadNamePrefix, int coreSize, int maxSize,
                                           int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }
}
