package com.sssok.renewal.application.media.upload.complete;

import com.sssok.renewal.application.media.upload.exception.UploadVerificationUnavailableException;
import com.sssok.renewal.outgoing.config.UploadHeadProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 업로드 완료 검증(저장소 HEAD)을 서버 전체가 공유하는 유한 풀에서 실행한다.
 * 요청마다 스레드를 만들지 않으므로 동시 HEAD 는 워커 수를 넘지 않는다. 워커에서는 DB 트랜잭션을 열지 않는다.
 */
@Component
public class UploadHeadWorkers {

    private static final String UNAVAILABLE_COUNTER = "sssok.upload.head.unavailable";

    private final ThreadPoolTaskExecutor executor;
    private final UploadHeadProperties properties;
    private final MeterRegistry metrics;

    public UploadHeadWorkers(@Qualifier("uploadHeadTaskExecutor") ThreadPoolTaskExecutor executor,
                             UploadHeadProperties properties, MeterRegistry metrics) {
        this.executor = executor;
        this.properties = properties;
        this.metrics = metrics;
        Gauge.builder("sssok.upload.head.active", executor, ThreadPoolTaskExecutor::getActiveCount).register(metrics);
        Gauge.builder("sssok.upload.head.queued", executor, ThreadPoolTaskExecutor::getQueueSize).register(metrics);
    }

    /**
     * 입력 순서대로 결과를 돌려준다. 제한 시간·큐 포화·중단이면 {@link UploadVerificationUnavailableException} 이다.
     * 한 요청이 큐를 독점하지 않도록 워커 수만큼 제출하고 결과를 모은 뒤 다음 묶음을 제출한다.
     */
    public <T, R> List<R> map(List<T> items, Function<T, R> check) {
        long deadlineNanos = System.nanoTime() + properties.validationTimeout().toNanos();
        List<R> results = new ArrayList<>(items.size());
        int chunkSize = properties.workers();
        for (int from = 0; from < items.size(); from += chunkSize) {
            List<T> chunk = items.subList(from, Math.min(items.size(), from + chunkSize));
            results.addAll(runChunk(chunk, check, deadlineNanos));
        }
        return results;
    }

    private <T, R> List<R> runChunk(List<T> chunk, Function<T, R> check, long deadlineNanos) {
        List<Future<R>> pending = new ArrayList<>(chunk.size());
        try {
            if (remainingNanos(deadlineNanos) <= 0) {
                throw new TimeoutException();
            }
            for (T item : chunk) {
                pending.add(executor.submit(() -> check.apply(item)));
            }
            List<R> results = new ArrayList<>(chunk.size());
            for (Future<R> future : pending) {
                long remaining = remainingNanos(deadlineNanos);
                if (remaining <= 0) {
                    throw new TimeoutException();
                }
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            }
            return results;
        } catch (RejectedExecutionException e) {
            throw unavailable("capacity");
        } catch (TimeoutException e) {
            throw unavailable("timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("interrupted");
        } catch (ExecutionException e) {
            throw rethrow(e.getCause());
        } finally {
            cancelUnfinished(pending);
        }
    }

    private long remainingNanos(long deadlineNanos) {
        return deadlineNanos - System.nanoTime();
    }

    // 실패한 요청이 남긴 작업이 큐를 차지하지 않도록 취소하고 큐에서도 치운다.
    private <R> void cancelUnfinished(List<Future<R>> pending) {
        pending.forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        executor.getThreadPoolExecutor().purge();
    }

    private UploadVerificationUnavailableException unavailable(String reason) {
        metrics.counter(UNAVAILABLE_COUNTER, "reason", reason).increment();
        return new UploadVerificationUnavailableException();
    }

    private RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }
}
