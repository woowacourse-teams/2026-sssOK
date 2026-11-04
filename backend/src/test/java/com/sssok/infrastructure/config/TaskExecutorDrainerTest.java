package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TaskExecutorDrainerTest {

    private final StaticApplicationContext context = new StaticApplicationContext();
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void tearDown() {
        release.countDown();
        context.close();
    }

    @Test
    void 컨텍스트를_닫으면_대기열의_작업까지_실행한_뒤_종료된다() {
        AtomicInteger done = new AtomicInteger();
        try (AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext(DrainConfig.class)) {
            ThreadPoolTaskExecutor worker = app.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            for (int i = 0; i < 5; i++) {
                worker.execute(() -> {
                    sleep(50);
                    done.incrementAndGet();
                });
            }
        }

        assertThat(done).hasValue(5);
    }

    @Test
    void 대기열에_남은_작업까지_실행한_뒤_멈춘다() {
        ThreadPoolTaskExecutor worker = register("applicationTaskExecutor", 1, 10);
        AtomicInteger done = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            worker.execute(() -> {
                sleep(20);
                done.incrementAndGet();
            });
        }

        drainer(Duration.ofSeconds(5)).stop();

        assertThat(done).hasValue(5);
        assertThat(worker.getThreadPoolExecutor().isTerminated()).isTrue();
    }

    @Test
    void 앞_단계_작업이_넘긴_후속_작업도_실행된다() {
        ThreadPoolTaskExecutor notification = register("roomNotificationTaskExecutor", 1, 10);
        ThreadPoolTaskExecutor thumbnail = register("imageThumbnailTaskExecutor", 1, 10);
        AtomicInteger notified = new AtomicInteger();
        thumbnail.execute(() -> {
            sleep(50);
            notification.execute(notified::incrementAndGet);
        });

        drainer(Duration.ofSeconds(5)).stop();

        assertThat(notified).hasValue(1);
    }

    @Test
    void 대기_상한을_넘기면_기다리지_않고_넘어간다() {
        ThreadPoolTaskExecutor worker = register("applicationTaskExecutor", 1, 10);
        worker.execute(this::awaitRelease);

        long started = System.nanoTime();
        drainer(Duration.ofMillis(200)).stop();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMillis).isLessThan(2_000);
    }

    @Test
    void 작업이_없으면_바로_멈춘다() {
        ThreadPoolTaskExecutor worker = register("applicationTaskExecutor", 1, 10);

        drainer(Duration.ofSeconds(5)).stop();

        assertThat(worker.getThreadPoolExecutor().isShutdown()).isTrue();
    }

    private TaskExecutorDrainer drainer(Duration timeout) {
        context.refresh();
        TaskExecutorDrainer drainer = new TaskExecutorDrainer(context, timeout);
        drainer.start();
        return drainer;
    }

    private ThreadPoolTaskExecutor register(String name, int poolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        context.getBeanFactory().registerSingleton(name, executor);
        return executor;
    }

    @Configuration
    static class DrainConfig {

        @Bean
        ThreadPoolTaskExecutor applicationTaskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            return executor;
        }

        @Bean
        TaskExecutorDrainer taskExecutorDrainer(ApplicationContext context) {
            return new TaskExecutorDrainer(context, Duration.ofSeconds(5));
        }
    }

    private void awaitRelease() {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
