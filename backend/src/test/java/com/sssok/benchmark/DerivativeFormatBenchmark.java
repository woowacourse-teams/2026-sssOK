package com.sssok.benchmark;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;

// #291 파생 이미지 포맷을 고르기 위한 측정기. 샘플 사진을 thumbnail·preview 크기로 줄인 뒤
// JPEG / WebP / AVIF 로 각각 인코딩해, 변환 시간 · 파일 크기 · SSIM 을 CSV 로 뱉는다.
//
// CI 에서 돌지 않게 테스트가 아닌 main 으로 둔다. 실행은 gradle 태스크로 한다:
//   ./gradlew derivativeFormatBenchmark -Psamples=<샘플 디렉터리> -Pout=<결과 CSV>
//
// 결과 해석과 최종 선택은 docs/backend/IMAGE_DERIVATIVE_FORMAT.md 에 있다.
public final class DerivativeFormatBenchmark {

    // 목록 타일(thumbnail)과 상세 모달(preview) 후보 너비. 샘플셋에 따라 달라지므로 밖에서 준다 —
    // 무손실 기준셋(Kodak)은 768x512 라 1600px 를 시켜도 늘리지 않아 측정이 되지 않는다.
    private static final int[] DEFAULT_TARGET_WIDTHS = {400, 1600};

    // JPEG·WebP 는 0~100 품질, AVIF 는 CRF(낮을수록 고품질)라 축이 다르다.
    // 서로 다른 축을 억지로 맞추는 대신, 각 포맷에서 쓸 만한 구간을 훑고 SSIM 으로 견준다.
    // 같은 SSIM 에서의 크기를 보간으로 구하려면 곡선이 촘촘해야 한다. 네 점만 찍고 눈대중으로
    // "SSIM 0.977 부근끼리" 맞추면, 고른 지점이 우연히 유리한 쪽이었는지 알 수가 없다.
    private static final float[] JPEG_QUALITIES =
        {0.50f, 0.60f, 0.65f, 0.70f, 0.75f, 0.80f, 0.85f, 0.90f};
    private static final float[] WEBP_QUALITIES =
        {0.50f, 0.60f, 0.65f, 0.70f, 0.75f, 0.80f, 0.85f, 0.90f};
    private static final int[] AVIF_CRFS = {20, 24, 28, 32, 36, 40, 44, 48};

    // 첫 회는 JIT 이 덜 달궈져 실제보다 느리다. 버리는 회차를 둔다.
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURED_ROUNDS = 5;

    private DerivativeFormatBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        Path sampleDir = Path.of(required("samples"));
        Path output = Path.of(System.getProperty("out", "derivative-format-benchmark.csv"));
        int[] targetWidths = targetWidths();

        List<Path> samples;
        try (var stream = Files.list(sampleDir)) {
            samples = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                    .matches(".*\\.(jpe?g|png)$"))
                .sorted(Comparator.comparing(Path::getFileName))
                .toList();
        }
        if (samples.isEmpty()) {
            throw new IllegalStateException("샘플이 없다: " + sampleDir);
        }
        System.out.printf("샘플 %d장, 대상 너비 %s%n",
            samples.size(), java.util.Arrays.toString(targetWidths));

        List<String> rows = new ArrayList<>();
        rows.add("sample,sourceWidth,sourceHeight,sourceBytes,targetWidth,targetHeight,"
            + "format,quality,decodeScaleMillis,encodeMillis,bytes,ssimGaussian,ssimUniform8");
        for (Path sample : samples) {
            rows.addAll(measure(sample, targetWidths));
            System.out.println("  " + sample.getFileName() + " 완료");
        }
        Files.writeString(output, String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
        System.out.println("결과: " + output.toAbsolutePath());
    }

    private static int[] targetWidths() {
        String configured = System.getProperty("widths");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_TARGET_WIDTHS;
        }
        return java.util.Arrays.stream(configured.split(","))
            .map(String::trim)
            .mapToInt(Integer::parseInt)
            .toArray();
    }

    private static List<String> measure(Path sample, int[] targetWidths) throws IOException {
        byte[] source = DerivativeCodec.read(sample);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));

        List<String> rows = new ArrayList<>();
        for (int targetWidth : targetWidths) {
            // 원본보다 크게 늘리지 않는다 — 프로덕션 축소 규칙과 같다.
            int width = Math.min(targetWidth, original.getWidth());
            long decodeScaleMillis = timeDecodeAndScale(source, width);
            BufferedImage reference = DerivativeCodec.toRgb(scale(source, width));

            for (float quality : JPEG_QUALITIES) {
                rows.add(row(sample, original, source, reference, decodeScaleMillis, "JPEG",
                    format(quality * 100), encode(() -> DerivativeCodec.encodeJpeg(reference, quality)),
                    DerivativeFormatBenchmark::decodeStandard));
            }
            for (float quality : WEBP_QUALITIES) {
                rows.add(row(sample, original, source, reference, decodeScaleMillis, "WEBP",
                    format(quality * 100), encode(() -> DerivativeCodec.encodeWebp(reference, quality)),
                    DerivativeFormatBenchmark::decodeStandard));
            }
            for (int crf : AVIF_CRFS) {
                rows.add(row(sample, original, source, reference, decodeScaleMillis, "AVIF",
                    "crf" + crf, encode(() -> DerivativeCodec.encodeAvif(reference, crf)),
                    encoded -> DerivativeCodec.decodeAvif(
                        encoded, reference.getWidth(), reference.getHeight())));
            }
        }
        return rows;
    }

    private static String row(Path sample, BufferedImage original, byte[] source,
                              BufferedImage reference, long decodeScaleMillis,
                              String format, String quality, Measured measured,
                              Decoder decoder) throws IOException {
        // 지표 하나에 결론을 걸지 않으려고 두 창을 다 적는다. 둘이 포맷 순위를 다르게 매기면
        // 그 결론은 지표를 고른 덕이지 포맷 덕이 아니다.
        BufferedImage decoded = decoder.decode(measured.encoded());
        double gaussian = Ssim.between(reference, decoded, Ssim.Window.GAUSSIAN);
        double uniform = Ssim.between(reference, decoded, Ssim.Window.UNIFORM_8);
        return String.join(",",
            sample.getFileName().toString(),
            String.valueOf(original.getWidth()),
            String.valueOf(original.getHeight()),
            String.valueOf(source.length),
            String.valueOf(reference.getWidth()),
            String.valueOf(reference.getHeight()),
            format,
            quality,
            String.valueOf(decodeScaleMillis),
            String.valueOf(measured.millis()),
            String.valueOf(measured.encoded().length),
            String.format(Locale.ROOT, "%.5f", gaussian),
            String.format(Locale.ROOT, "%.5f", uniform));
    }

    private static BufferedImage decodeStandard(byte[] encoded) throws IOException {
        return DerivativeCodec.decode(encoded);
    }

    // 여러 번 돌린 뒤 중앙값을 쓴다. 평균은 GC 가 한 번 끼면 통째로 끌려간다.
    private static Measured encode(Encoder encoder) throws IOException {
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            encoder.encode();
        }
        long[] millis = new long[MEASURED_ROUNDS];
        byte[] encoded = null;
        for (int i = 0; i < MEASURED_ROUNDS; i++) {
            long start = System.nanoTime();
            encoded = encoder.encode();
            millis[i] = (System.nanoTime() - start) / 1_000_000;
        }
        java.util.Arrays.sort(millis);
        return new Measured(millis[MEASURED_ROUNDS / 2], encoded);
    }

    // 디코딩 + 축소는 어느 포맷을 고르든 똑같이 치르는 비용이다. 포맷별 인코딩 시간과 섞이지
    // 않도록 따로 재서, 전체 파이프라인에서 포맷 선택이 차지하는 몫을 볼 수 있게 한다.
    private static long timeDecodeAndScale(byte[] source, int width) throws IOException {
        scale(source, width);
        long start = System.nanoTime();
        scale(source, width);
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static BufferedImage scale(byte[] source, int width) throws IOException {
        return Thumbnails.of(new ByteArrayInputStream(source))
            .width(width)
            .keepAspectRatio(true)
            .asBufferedImage();
    }

    private static String format(float value) {
        return String.valueOf(Math.round(value));
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("-D" + key + " 이 필요하다.");
        }
        return value;
    }

    private record Measured(long millis, byte[] encoded) {
    }

    @FunctionalInterface
    private interface Encoder {
        byte[] encode() throws IOException;
    }

    @FunctionalInterface
    private interface Decoder {
        BufferedImage decode(byte[] encoded) throws IOException;
    }
}
