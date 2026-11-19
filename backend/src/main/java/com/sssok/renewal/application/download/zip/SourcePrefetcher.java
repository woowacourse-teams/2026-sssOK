package com.sssok.renewal.application.download.zip;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

// 압축하는 동안 다음 원본들의 GET 을 미리, 여러 개 겹쳐 열어 둔다(#666).
// 원본마다 요청을 보내고 첫 바이트를 받기까지의 대기가 압축 시간의 대부분이라, 워커가 지금 파일을 zip 에 쓰는 동안
// depth 개까지의 다음 파일 GET 이 함께 대기하게 한다. 본문은 워커가 읽을 때 받으므로 힙에 원본을 쌓지 않는다.
//
// - 워커는 대상 순서대로 꺼낸다. zip 항목 순서와 진행률은 기존과 같다.
// - GET 을 시작하는 순서도 대상 순서다. 앞 GET 이 시작된 뒤, 끝났거나 START_GAP 만큼 지나야 다음을 시작한다.
//   순서대로 시작해야 "n 번째로 여는 원본" 에 대한 동작(실패·지연)이 기존과 같은 파일에서 일어난다.
// - 열다가 실패하면 워커가 그 파일을 꺼낼 때 원래 예외를 그대로 던진다(실패 사유 유지). 그 뒤로는 더 열지 않는다.
// - 실행기가 받지 않거나 depth 가 0 이면 워커가 꺼낼 때 직접 연다(기존 동작).
// - close() 하면 더 열지 않고, 열어 두었거나 열던 중인 스트림은 열리는 대로 닫아 커넥션을 돌려준다.
// 한 워커 스레드만 next()·close() 를 부른다.
@Slf4j
final class SourcePrefetcher<T> implements AutoCloseable {

    static final long START_GAP_MILLIS = 10;
    private static final long ENTER_TIMEOUT_MILLIS = 1000;

    private final List<T> targets;
    private final Function<T, InputStream> opener;
    private final Executor executor;
    private final int depth;
    private final List<Slot> slots = new ArrayList<>();
    private int nextToTake;
    private boolean stopped;
    private volatile boolean closed;

    private SourcePrefetcher(List<T> targets, Function<T, InputStream> opener, Executor executor, int depth) {
        this.targets = List.copyOf(targets);
        this.opener = opener;
        this.executor = executor;
        this.depth = depth;
    }

    static <T> SourcePrefetcher<T> start(List<T> targets, Function<T, InputStream> opener,
                                         Executor executor, int depth) {
        SourcePrefetcher<T> prefetcher = new SourcePrefetcher<>(targets, opener, executor, depth);
        prefetcher.dispatchAhead();
        return prefetcher;
    }

    // 다음 대상의 원본 스트림. 대상 순서대로 꺼낸다.
    InputStream next() {
        if (nextToTake >= targets.size()) {
            throw new IllegalStateException("더 꺼낼 원본이 없습니다");
        }
        if (nextToTake >= slots.size()) {
            dispatch(nextToTake, false);
        }
        Slot slot = slots.get(nextToTake++);
        // 꺼낸 파일이 열린 뒤에 다음을 시작해야 동시에 열리는 GET 이 depth 개를 넘지 않는다.
        InputStream stream = slot.join();
        dispatchAhead();
        return stream;
    }

    private void dispatchAhead() {
        while (!stopped && depth > 0 && slots.size() < targets.size() && slots.size() < nextToTake + depth) {
            dispatch(slots.size(), true);
        }
    }

    private void dispatch(int index, boolean ahead) {
        if (ahead) {
            awaitStartGap();
            if (stopped) {
                return;
            }
        }
        Slot slot = new Slot();
        slots.add(slot);
        T target = targets.get(index);
        if (ahead) {
            try {
                executor.execute(() -> slot.open(target));
                return;
            } catch (RejectedExecutionException e) {
                log.debug("원본 미리 열기 실행기가 가득 차 직접 엽니다. index={}", index);
                stopped = true;
            }
        }
        slot.open(target);
    }

    // 바로 앞 GET 이 실제로 시작될 때까지 기다리고, 아직 끝나지 않았으면 START_GAP 만큼 더 기다린다.
    private void awaitStartGap() {
        if (slots.isEmpty()) {
            return;
        }
        Slot previous = slots.get(slots.size() - 1);
        try {
            previous.entered.await(ENTER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            previous.result.get(START_GAP_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            // 아직 열리는 중이거나 실패했다. 실패는 그 파일을 꺼낼 때 던진다.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopped = true;
        }
        if (previous.result.isCompletedExceptionally()) {
            stopped = true;
        }
    }

    @Override
    public void close() {
        closed = true;
        stopped = true;
        for (int i = nextToTake; i < slots.size(); i++) {
            slots.get(i).result.thenAccept(SourcePrefetcher::closeQuietly);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException | RuntimeException e) {
            log.debug("미리 연 원본 스트림을 닫지 못했습니다", e);
        }
    }

    private final class Slot {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CompletableFuture<InputStream> result = new CompletableFuture<>();

        private void open(T target) {
            entered.countDown();
            if (closed) {
                result.completeExceptionally(new IllegalStateException("압축이 끝나 원본을 열지 않았습니다"));
                return;
            }
            try {
                result.complete(opener.apply(target));
            } catch (RuntimeException e) {
                result.completeExceptionally(e);
            } catch (Error e) {
                result.completeExceptionally(new IllegalStateException(e));
            }
        }

        private InputStream join() {
            try {
                return result.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                throw e;
            }
        }
    }
}
