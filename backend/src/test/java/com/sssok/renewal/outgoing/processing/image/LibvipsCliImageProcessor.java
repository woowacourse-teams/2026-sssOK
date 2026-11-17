package com.sssok.renewal.outgoing.processing.image;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivativeSpec;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImage;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.DerivedImages;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

/** JPEG만 CLI로 처리한다. 원본을 한 번 디코딩한 .v 파일에서 두 파생본을 만든다. */
@Component
@ConditionalOnProperty(name = "media.image.processor", havingValue = "libvips-cli")
public class LibvipsCliImageProcessor implements ImageDerivativeEngine {
    private static final Logger log = LoggerFactory.getLogger(LibvipsCliImageProcessor.class);
    private final String executable;
    private final Duration timeout;
    private final Path tempRoot;
    private final ImageDerivativeOperations operations;

    @Autowired
    public LibvipsCliImageProcessor(ImageDerivativeOperations operations,
            @Value("${media.image.cli.executable:vips}") String executable,
            @Value("${media.image.cli.timeout:20s}") String timeout) {
        this(operations, executable, DurationStyle.detectAndParse(timeout));
    }

    public LibvipsCliImageProcessor(ImageDerivativeOperations operations, String executable, Duration timeout) {
        this(operations, executable, timeout, Path.of(System.getProperty("java.io.tmpdir")));
    }

    LibvipsCliImageProcessor(ImageDerivativeOperations operations, String executable,
                            Duration timeout, Path tempRoot) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("CLI timeout must be between zero and five minutes");
        }
        this.operations = operations;
        this.executable = executable;
        this.timeout = timeout;
        this.tempRoot = tempRoot;
    }

    @Override
    public boolean supports(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview) {
        return source.length >= 3 && (source[0] & 255) == 255 && (source[1] & 255) == 216
            && (source[2] & 255) == 255 && thumbnail.format() == DerivativeFormat.WEBP
            && (preview == null || preview.format() == DerivativeFormat.WEBP);
    }

    @Override
    public DerivedImages derive(byte[] source, DerivativeSpec thumbnail, DerivativeSpec preview) {
        validate(thumbnail);
        if (preview != null) validate(preview);
        if (source.length == 0 || source.length > 150 * 1024 * 1024) {
            throw new IllegalArgumentException("JPEG input size exceeds limit");
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        Path directory = null;
        try {
            Size original = sourceSize(source);
            Size base = original.fit(Math.max(thumbnail.maxWidth(), preview == null ? 0 : preview.maxWidth()));
            if ((long) original.width * original.height > 100_000_000
                || base.width > 16383 || base.height > 16383 || (long) base.width * base.height > 16_000_000) {
                throw new IllegalArgumentException("JPEG dimensions exceed processing limit");
            }
            directory = Files.createTempDirectory(tempRoot, "sssok-vips-");
            Path input = directory.resolve("source.jpg");
            Path pixels = directory.resolve("pixels.v");
            Files.write(input, source);
            run(directory, deadline, "thumbnail", input.toString(), pixels.toString(),
                Integer.toString(base.width), "--height", Integer.toString(base.height),
                "--size", "force", "--fail-on", "error");
            DerivedImage small = encode(directory, deadline, pixels, base, original.fit(thumbnail.maxWidth()),
                thumbnail, "thumbnail.webp");
            DerivedImage large = preview == null ? null : encode(directory, deadline, pixels, base,
                original.fit(preview.maxWidth()), preview, "preview.webp");
            return new DerivedImages(original.width, original.height, small, large);
        } catch (IOException e) {
            // 변환·프로세스 실패를 손상된 원본으로 판단해 영구 FAILED 처리하지 않는다.
            throw new UncheckedIOException(e);
        } finally {
            if (directory != null) cleanup(directory);
        }
    }

    private Size sourceSize(byte[] source) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unreadable JPEG header");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw new IOException("Invalid JPEG dimensions");
                var orientation = operations.readOrientation(source);
                return new Size(orientation.displayWidthOf(width, height), orientation.displayHeightOf(width, height));
            } finally {
                reader.dispose();
            }
        }
    }

    private DerivedImage encode(Path directory, long deadline, Path pixels, Size base, Size target,
                                DerivativeSpec spec, String filename) throws IOException {
        Path output = directory.resolve(filename);
        String options = "[Q=" + Math.round(spec.quality() * 100) + ",effort=0,strip]";
        run(directory, deadline, "resize", pixels.toString(), output + options,
            String.format(Locale.ROOT, "%.12f", (double) target.width / base.width),
            "--vscale", String.format(Locale.ROOT, "%.12f", (double) target.height / base.height),
            "--kernel", "linear");
        long bytes = Files.size(output);
        if (bytes <= 0 || bytes > 64 * 1024 * 1024) throw new IOException("Invalid WebP output size");
        // 출력 헤더만 확인하고 검증을 위해 결과를 다시 디코딩하지 않는다.
        try (var input = ImageIO.createImageInputStream(output.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unreadable WebP output");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("webp") || reader.getWidth(0) != target.width
                    || reader.getHeight(0) != target.height) throw new IOException("Unexpected WebP dimensions");
            } finally {
                reader.dispose();
            }
        }
        return new DerivedImage(Files.readAllBytes(output), DerivativeFormat.WEBP);
    }

    private void run(Path directory, long deadline, String... args) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Image processing interrupted");
        if (System.nanoTime() >= deadline) throw new IOException("Image processing timed out");
        var command = new ArrayList<String>();
        command.add(executable);
        command.addAll(Arrays.asList(args));
        command.add("--vips-concurrency=1");
        command.add("--vips-cache-max=0");
        Path error = directory.resolve("stderr.log");
        var builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(error.toFile());
        var process = builder.start();
        try {
            if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new IOException("Image processing timed out");
            }
            if (process.exitValue() != 0) {
                // 진단 메시지에 크기 상한을 두고 원본 바이트는 기록하지 않는다.
                try (var stream = Files.newInputStream(error)) {
                    throw new IOException("libvips exited " + process.exitValue() + ": "
                        + new String(stream.readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image processing interrupted", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                // 삭제 전에 자식 프로세스 종료를 기다린다. 인터럽트 상태는 호출자에게 보존한다.
                boolean interrupted = Thread.interrupted();
                try {
                    while (process.isAlive()) {
                        try {
                            process.waitFor();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                } finally {
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void validate(DerivativeSpec spec) {
        if (spec.maxWidth() <= 0 || spec.maxWidth() > 16383 || !Float.isFinite(spec.quality())
            || spec.quality() <= 0 || spec.quality() > 1 || spec.format() != DerivativeFormat.WEBP) {
            throw new IllegalArgumentException("Invalid WebP derivative specification");
        }
    }

    private static void cleanup(Path directory) {
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("libvips 임시 파일 정리 실패: {}", directory, e);
        }
    }

    private record Size(int width, int height) {
        Size fit(int maxWidth) {
            int w = Math.min(width, maxWidth);
            return new Size(w, Math.max(1, (int) Math.round((double) height * w / width)));
        }
    }
}
