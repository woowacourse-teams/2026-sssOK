package com.sssok.renewal.outgoing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RoomNotificationConfig {

    // 외부 HTTP 라 느릴 수 있어 썸네일·zip 이 쓰는 공용 풀과 나눈다.
    @Bean(name = "roomNotificationTaskExecutor")
    public AsyncTaskExecutor roomNotificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("room-notification-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        return executor;
    }
}
