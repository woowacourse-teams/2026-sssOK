package com.sssok.renewal.application.download.zip;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourcePrefetcherTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> openOrder = new CopyOnWriteArrayList<>();
    private final List<TrackedStream> streams = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 워커가_꺼내기_전에_다음_원본을_대상_순서대로_미리_연다() throws IOException {
        try (SourcePrefetcher<String> prefetcher =
                 SourcePrefetcher.start(List.of("a", "b", "c"), this::open, executor, 2)) {
            // 아무것도 꺼내지 않아도 depth(2)만큼은 열려 있고, 그 이상은 열지 않는다.
            waitUntil(() -> openOrder.size() == 2);
            assertThat(openOrder).containsExactly("a", "b");

            assertThat(contentOf(prefetcher.next())).isEqualTo("a");
            assertThat(contentOf(prefetcher.next())).isEqualTo("b");
            assertThat(contentOf(prefetcher.next())).isEqualTo("c");
        }
        assertThat(openOrder).containsExactly("a", "b", "c");
    }

    @Test
    void 열다가_실패하면_그_파일을_꺼낼_때_원래_예외를_던지고_더_열지_않는다() throws IOException {
        Function<String, InputStream> opener = key -> {
            if (key.equals("b")) {
                openOrder.add(key);
                throw new IllegalStateException("원본 없음");
            }
            return open(key);
        };
        try (SourcePrefetcher<String> prefetcher =
                 SourcePrefetcher.start(List.of("a", "b", "c"), opener, executor, 2)) {
            assertThat(contentOf(prefetcher.next())).isEqualTo("a");
            assertThatThrownBy(prefetcher::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("원본 없음");
        }
        assertThat(openOrder).containsExactly("a", "b");
    }

    @Test
    void 중간에_닫으면_열어_둔_스트림과_열던_중인_스트림을_모두_닫는다() throws InterruptedException {
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Function<String, InputStream> opener = key -> {
            if (key.equals("c")) {
                opening.countDown();
                await(release);
            }
            return open(key);
        };
        SourcePrefetcher<String> prefetcher =
            SourcePrefetcher.start(List.of("a", "b", "c", "d"), opener, executor, 2);
        prefetcher.next();
        assertThat(opening.await(2, TimeUnit.SECONDS)).isTrue();

        prefetcher.close();
        release.countDown();

        waitUntil(() -> streams.size() == 3 && streams.stream().skip(1).allMatch(TrackedStream::isClosed));
        assertThat(openOrder).doesNotContain("d");
    }

    @Test
    void 실행기가_받지_않으면_워커_스레드에서_직접_순서대로_연다() throws IOException {
        executor.shutdown();
        String worker = Thread.currentThread().getName();
        List<String> openThreads = new CopyOnWriteArrayList<>();
        try (SourcePrefetcher<String> prefetcher = SourcePrefetcher.start(List.of("a", "b"), key -> {
            openThreads.add(Thread.currentThread().getName());
            return open(key);
        }, executor, 2)) {
            assertThat(contentOf(prefetcher.next())).isEqualTo("a");
            assertThat(contentOf(prefetcher.next())).isEqualTo("b");
        }
        assertThat(openOrder).containsExactly("a", "b");
        assertThat(openThreads).containsOnly(worker);
    }

    @Test
    void 원본_GET_대기를_depth_개까지_겹친다() throws IOException {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        Function<String, InputStream> slowOpener = key -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sleep(100);
            inFlight.decrementAndGet();
            return open(key);
        };
        List<String> keys = List.of("a", "b", "c", "d", "e", "f");

        long start = System.nanoTime();
        try (SourcePrefetcher<String> prefetcher = SourcePrefetcher.start(keys, slowOpener, executor, 3)) {
            for (String key : keys) {
                assertThat(contentOf(prefetcher.next())).isEqualTo(key);
            }
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(openOrder).containsExactlyElementsOf(keys);
        assertThat(maxInFlight.get()).isEqualTo(3);
        // 하나씩 열면 600ms 다. 3개씩 겹치면 그 절반 아래로 끝난다.
        assertThat(elapsedMillis).isLessThan(400);
    }

    @Test
    void 미리_열_수가_0이면_실행기를_쓰지_않는다() throws IOException {
        AtomicInteger submitted = new AtomicInteger();
        try (SourcePrefetcher<String> prefetcher = SourcePrefetcher.start(List.of("a"), this::open,
            command -> {
                submitted.incrementAndGet();
                throw new RejectedExecutionException();
            }, 0)) {
            assertThat(contentOf(prefetcher.next())).isEqualTo("a");
        }
        assertThat(submitted).hasValue(0);
    }

    private InputStream open(String key) {
        openOrder.add(key);
        TrackedStream stream = new TrackedStream(key);
        streams.add(stream);
        return stream;
    }

    private static String contentOf(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("2초 안에 조건을 만족하지 않았다");
            }
            Thread.onSpinWait();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class TrackedStream extends ByteArrayInputStream {

        private volatile boolean closed;

        private TrackedStream(String content) {
            super(content.getBytes());
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }
    }
}
