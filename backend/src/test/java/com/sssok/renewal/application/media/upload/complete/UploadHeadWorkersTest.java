package com.sssok.renewal.application.media.upload.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.application.media.upload.exception.UploadVerificationUnavailableException;
import com.sssok.renewal.outgoing.config.UploadHeadConfig;
import com.sssok.renewal.outgoing.config.UploadHeadProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class UploadHeadWorkersTest {

    private static UploadHeadProperties properties(int workers, int queueCapacity, Duration validationTimeout) {
        return new UploadHeadProperties(workers, queueCapacity, validationTimeout, null);
    }

    private static ThreadPoolTaskExecutor executorOf(UploadHeadProperties properties) {
        ThreadPoolTaskExecutor executor = new UploadHeadConfig().uploadHeadTaskExecutor(properties);
        executor.initialize();
        return executor;
    }

    @Test
    void 동시_요청이_워커_4개를_나눠_쓰고_입력_순서를_지킨다() throws Exception {
        UploadHeadProperties properties = properties(4, 64, Duration.ofSeconds(5));
        ThreadPoolTaskExecutor executor = executorOf(properties);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            UploadHeadWorkers workers = new UploadHeadWorkers(executor, properties, metrics);
            CountDownLatch firstWorkersStarted = new CountDownLatch(4);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger active = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            List<Integer> items = IntStream.range(0, 12).boxed().toList();

            List<Future<List<Integer>>> results = new ArrayList<>();
            for (int request = 0; request < 8; request++) {
                results.add(callers.submit(() -> workers.map(items, item -> {
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    try {
                        firstWorkersStarted.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("워커 시작 확인 대기 초과");
                        }
                        return item;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    } finally {
                        active.decrementAndGet();
                    }
                })));
            }

            try {
                assertThat(firstWorkersStarted.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(peak.get()).isEqualTo(4);
            } finally {
                release.countDown();
            }

            for (Future<List<Integer>> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).containsExactlyElementsOf(items);
            }
            assertThat(peak.get()).isEqualTo(4);
        } finally {
            callers.shutdownNow();
            executor.shutdown();
            metrics.close();
        }
    }

    @Test
    void 제한_시간이_지나면_작업을_취소하고_풀은_다시_쓸_수_있다() throws Exception {
        UploadHeadProperties properties = properties(1, 1, Duration.ofMillis(50));
        ThreadPoolTaskExecutor executor = executorOf(properties);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        try {
            UploadHeadWorkers workers = new UploadHeadWorkers(executor, properties, metrics);
            CountDownLatch interrupted = new CountDownLatch(1);
            CountDownLatch blocked = new CountDownLatch(1);

            assertThatThrownBy(() -> workers.map(List.of(1), item -> {
                try {
                    blocked.await();
                    return item;
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            })).isInstanceOf(UploadVerificationUnavailableException.class);

            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(workers.map(List.of(2), item -> item)).containsExactly(2);
            assertThat(metrics.get("sssok.upload.head.unavailable").tag("reason", "timeout").counter().count())
                .isEqualTo(1);
        } finally {
            executor.shutdown();
            metrics.close();
        }
    }

    @Test
    void 큐가_가득_차면_거절된_작업은_실행하지_않는다() throws Exception {
        UploadHeadProperties properties = properties(1, 1, Duration.ofSeconds(5));
        ThreadPoolTaskExecutor executor = executorOf(properties);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        try {
            executor.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> { });

            UploadHeadWorkers workers = new UploadHeadWorkers(executor, properties, metrics);
            AtomicInteger calls = new AtomicInteger();

            assertThatThrownBy(() -> workers.map(List.of(1), item -> calls.incrementAndGet()))
                .isInstanceOf(UploadVerificationUnavailableException.class);
            assertThat(calls).hasValue(0);
            assertThat(metrics.get("sssok.upload.head.unavailable").tag("reason", "capacity").counter().count())
                .isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdown();
            metrics.close();
        }
    }

    @Test
    void 잘못된_설정은_시작할_때_거부한다() {
        assertThatThrownBy(() -> properties(0, 1, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(33, 1, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(4, -1, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties(4, 1, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadHeadProperties(4, 1, Duration.ofSeconds(1), Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
