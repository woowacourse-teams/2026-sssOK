package com.sssok.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 작업용 스레드 풀.
 *
 * <p>여기 만드는 실행기에는 {@code TaskDecorator} 를 걸지 않는다 — 요청 스레드의 MDC(requestId 등)를
 * 워커로 옮기지 않겠다는 결정이다. 이 풀이 도는 작업(썸네일 생성·zip 압축·스토리지 정리)은 요청보다
 * 오래 살고, 요청 하나가 여러 건을 던지고, 실패하면 나중에 다시 실행된다. 그 로그에 요청 ID 를
 * 달면 요청은 이미 200 으로 끝났는데 같은 ID 의 로그가 한참 뒤에 또 나서, ID 하나로 요청 하나를
 * 집어내는 추적이 흐려진다. 작업 쪽은 작업 자신의 식별자(jobId·mediaId)로 따라간다.
 *
 * <p>MDC 를 넘기지 않으면 워커 스레드는 요청 값을 아예 보지 못해, 스레드를 재사용해도 앞 요청의
 * 값이 다음 작업 로그에 새지 않는다. {@code MdcIsolationTest} 가 이 두 가지를 확인한다.
 */
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
