package com.sssok.renewal.application.download.zip;

import com.sssok.renewal.application.download.zip.CompressionStageClock.Stage;
import com.sssok.renewal.outgoing.storage.AbortableOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CompressionStageClockTest {

    private static final long SLOW_MILLIS = 60;

    @Test
    void 원본_읽기와_업로드_대기는_압축_시간에서_빠지고_각자_구간에_잡힌다() throws IOException {
        CompressionStageClock clock = new CompressionStageClock();
        AbortableOutputStream out = clock.openUpload(() -> slowUpload());

        try (InputStream in = clock.openSource(() -> {
            sleep();
            return slowSource(new byte[]{1, 2, 3});
        })) {
            clock.write(() -> in.transferTo(out));
        }
        clock.progress(CompressionStageClockTest::sleep);
        clock.fileDone();

        Map<Stage, Duration> elapsed = clock.elapsed();
        assertThat(elapsed.get(Stage.OPEN)).isGreaterThanOrEqualTo(Duration.ofMillis(SLOW_MILLIS));
        assertThat(elapsed.get(Stage.READ)).isGreaterThanOrEqualTo(Duration.ofMillis(SLOW_MILLIS));
        assertThat(elapsed.get(Stage.UPLOAD)).isGreaterThanOrEqualTo(Duration.ofMillis(SLOW_MILLIS));
        assertThat(elapsed.get(Stage.PROGRESS)).isGreaterThanOrEqualTo(Duration.ofMillis(SLOW_MILLIS));
        // 쓰기 구간 대부분이 읽기·업로드 대기라, 남은 압축 시간은 그보다 훨씬 짧다.
        assertThat(elapsed.get(Stage.DEFLATE)).isLessThan(Duration.ofMillis(SLOW_MILLIS));
        assertThat(clock.files()).isEqualTo(1);
    }

    @Test
    void 감싼_업로드_스트림도_abort_를_그대로_넘긴다() {
        CompressionStageClock clock = new CompressionStageClock();
        boolean[] aborted = {false};
        AbortableOutputStream out = clock.openUpload(() -> new AbortableOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public void abort() {
                aborted[0] = true;
            }
        });

        out.abort();

        assertThat(aborted[0]).isTrue();
    }

    private static InputStream slowSource(byte[] content) {
        return new ByteArrayInputStream(content) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                if (pos == 0) {
                    sleep();
                }
                return super.read(b, off, len);
            }
        };
    }

    private static AbortableOutputStream slowUpload() {
        return new AbortableOutputStream() {
            private final OutputStream sink = OutputStream.nullOutputStream();

            @Override
            public void write(int b) throws IOException {
                sink.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                sleep();
                sink.write(b, off, len);
            }

            @Override
            public void abort() {
            }
        };
    }

    private static void sleep() {
        try {
            Thread.sleep(SLOW_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
