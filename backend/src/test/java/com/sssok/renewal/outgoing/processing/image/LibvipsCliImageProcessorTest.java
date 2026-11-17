package com.sssok.renewal.outgoing.processing.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("native-image")
class LibvipsCliImageProcessorTest extends ThumbnailatorImageProcessorTest {
    @TempDir Path temporary;
    private static final DerivativeSpec WEBP = new DerivativeSpec(400, DerivativeFormat.WEBP, .8f);

    @Override
    protected ThumbnailatorImageProcessor createProcessor() {
        return new ThumbnailatorImageProcessor(new ImageDerivativeOperations(), Optional.of(
            new LibvipsCliImageProcessor(new ImageDerivativeOperations(), "vips", Duration.ofSeconds(20))));
    }

    @Test
    void 성공과_실패_후_임시파일을_정리한다() throws Exception {
        var cli = cli("vips", Duration.ofSeconds(20));
        assertThat(cli.derive(jpeg(), WEBP, WEBP).hasPreview()).isTrue();
        assertEmpty();
        assertThatThrownBy(() -> cli("/missing/vips", Duration.ofSeconds(20)).derive(jpeg(), WEBP, null))
            .isInstanceOf(UncheckedIOException.class);
        assertEmpty();
    }

    @Test
    void 성공_종료해도_출력이_없으면_실패한다() throws Exception {
        assertThatThrownBy(() -> cli("/bin/true", Duration.ofSeconds(20)).derive(jpeg(), WEBP, null))
            .isInstanceOf(UncheckedIOException.class);
        assertEmpty();
    }

    @Test
    void 시간초과시_프로세스를_종료하고_임시파일을_정리한다() throws Exception {
        Path script = sleeper();
        assertThatThrownBy(() -> cli(script.toString(), Duration.ofMillis(500)).derive(jpeg(), WEBP, null))
            .isInstanceOf(UncheckedIOException.class).hasMessageContaining("timed out");
        assertProcessStopped();
    }

    @Test
    void 인터럽트시_프로세스와_임시파일을_정리하고_인터럽트를_보존한다() throws Exception {
        Path script = sleeper();
        var failure = new AtomicReference<Throwable>();
        var preserved = new java.util.concurrent.atomic.AtomicBoolean();
        byte[] source = jpeg();
        Thread worker = new Thread(() -> {
            try {
                cli(script.toString(), Duration.ofSeconds(20)).derive(source, WEBP, null);
            } catch (Throwable e) {
                failure.set(e);
                preserved.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (!Files.exists(temporary.resolve("pid")) && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(Files.exists(temporary.resolve("pid"))).isTrue();
        } finally {
            worker.interrupt();
            worker.join(5000);
        }
        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(UncheckedIOException.class);
        assertThat(preserved.get()).isTrue();
        assertProcessStopped();
    }

    @Test
    void 손상된_JPEG_실패후_다음_작업은_성공한다() throws Exception {
        var cli = cli("vips", Duration.ofSeconds(20));
        assertThatThrownBy(() -> cli.derive(new byte[] {(byte)255, (byte)216, (byte)255}, WEBP, null))
            .isInstanceOf(UncheckedIOException.class);
        assertThat(cli.derive(jpeg(), WEBP, null).sourceWidth()).isEqualTo(120);
        assertEmpty();
    }

    private LibvipsCliImageProcessor cli(String executable, Duration timeout) {
        return new LibvipsCliImageProcessor(new ImageDerivativeOperations(), executable, timeout, temporary);
    }

    private Path sleeper() throws Exception {
        Path script = temporary.resolve("sleep-vips");
        Files.writeString(script, "#!/bin/sh\necho $$ > '" + temporary.resolve("pid") + "'\nexec sleep 30\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private void assertProcessStopped() throws Exception {
        long pid = Long.parseLong(Files.readString(temporary.resolve("pid")).trim());
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        Files.delete(temporary.resolve("pid"));
        Files.delete(temporary.resolve("sleep-vips"));
        assertEmpty();
    }

    private void assertEmpty() throws Exception {
        try (var files = Files.list(temporary)) { assertThat(files.toList()).isEmpty(); }
    }

    private byte[] jpeg() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        return bytes.toByteArray();
    }
}
