package com.sssok.benchmark;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;

// 한 장씩 재는 DerivativeFormatBenchmark 만으로는 포맷을 고를 수 없다.
//
// AVIF 인코더(SVT-AV1)는 안에서 코어를 여러 개 쓰고 WebP 인코더는 한 코어만 쓴다. 그래서 한가한
// 장비에서 한 장만 재면 둘의 시간이 비슷하게 나오는데, 실제 워커 풀은 spring.task.execution 의
// core-size(4)만큼 동시에 돈다. 그 상태에서도 같은지를 봐야 업로드가 몰릴 때 무슨 일이 생기는지 안다.
//
//   ./gradlew derivativeConcurrencyBenchmark -Psamples=<샘플 디렉터리> \
//       -Pwebp=<품질> -Pavif=<CRF> -Pout=<결과 CSV>
//
// 결과 해석과 최종 선택은 docs/backend/IMAGE_DERIVATIVE_FORMAT.md 에 있다.
public final class DerivativeConcurrencyBenchmark {

    private static final int TARGET_WIDTH = 1600;

    // 두 포맷의 처리량을 견주려면 같은 화질에서 재야 한다. 한쪽이 더 곱게 인코딩하고 있으면
    // 느린 것이 포맷 탓인지 화질 탓인지 갈라 볼 수 없다. 그래서 기본값을 박아 두지 않고
    // DerivativeFormatBenchmark 의 곡선에서 고른 지점을 밖에서 준다.
    //   -Pwebp=<0~100 품질> -Pavif=<CRF, 낮을수록 고품질>
    // 고른 지점이 실제로 같은 화질인지는 측정 전에 SSIM 을 재서 결과에 같이 남긴다.
    private static final float WEBP_QUALITY = requiredInt("webp") / 100f;
    private static final int AVIF_CRF = requiredInt("avif");

    // 1 은 한가한 장비, 4 는 spring.task.execution.pool.core-size 와 같은 값,
    // 8 은 max-size 까지 늘어난 피크.
    private static final int[] CONCURRENCIES = {1, 4, 8};

    // 한 번만 재면 그때 백그라운드에서 뭐가 돌았는지에 따라 값이 흔들린다. 여러 번 재서
    // 평균과 표준편차를 같이 내야, 두 포맷의 차이가 잡음보다 큰지 말할 수 있다.
    private static final int REPEATS = 9;

    private DerivativeConcurrencyBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Path sampleDir = Path.of(System.getProperty("samples"));
        Path output = Path.of(System.getProperty("out", "derivative-concurrency-benchmark.csv"));

        List<BufferedImage> scaled = new ArrayList<>();
        try (var stream = Files.list(sampleDir)) {
            for (Path sample : stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(Path::getFileName)).toList()) {
                byte[] source = Files.readAllBytes(sample);
                // 원본보다 크게 늘리지 않는다 — 프로덕션 축소 규칙, DerivativeFormatBenchmark 와 같다.
                // 늘려 놓고 재면 없던 픽셀을 인코딩하는 시간이 섞인다.
                int sourceWidth = ImageIO.read(new ByteArrayInputStream(source)).getWidth();
                int width = Math.min(TARGET_WIDTH, sourceWidth);
                scaled.add(DerivativeCodec.toRgb(Thumbnails.of(new ByteArrayInputStream(source))
                    .width(width).keepAspectRatio(true).asBufferedImage()));
            }
        }
        System.out.printf("샘플 %d장을 최대 %dpx 로 줄여 놓고 시작한다 (원본이 작으면 그대로)%n",
            scaled.size(), TARGET_WIDTH);

        double webpSsim = meanSsim(scaled, image -> DerivativeCodec.decode(
            DerivativeCodec.encodeWebp(image, WEBP_QUALITY)));
        double avifSsim = meanSsim(scaled, image -> DerivativeCodec.decodeAvif(
            DerivativeCodec.encodeAvif(image, AVIF_CRF), image.getWidth(), image.getHeight()));
        System.out.printf("화질 대조 — WebP q%.0f SSIM %.4f / AVIF crf%d SSIM %.4f (차이 %.4f)%n",
            WEBP_QUALITY * 100, webpSsim, AVIF_CRF, avifSsim, Math.abs(webpSsim - avifSsim));

        List<String> rows = new ArrayList<>();
        rows.add("format,setting,ssimGaussian,concurrency,images,repeats,"
            + "meanWallMillis,stdevWallMillis,meanImagesPerSecond,stdevImagesPerSecond");
        for (int concurrency : CONCURRENCIES) {
            rows.add(run("WEBP", format("q%.0f", WEBP_QUALITY * 100), webpSsim, concurrency, scaled,
                image -> DerivativeCodec.encodeWebp(image, WEBP_QUALITY)));
            rows.add(run("AVIF", "crf" + AVIF_CRF, avifSsim, concurrency, scaled,
                image -> DerivativeCodec.encodeAvif(image, AVIF_CRF)));
        }
        Files.writeString(output, String.join("\n", rows) + "\n");
        System.out.println("결과: " + output.toAbsolutePath());
    }

    // 설정과 그 설정의 SSIM 을 행마다 같이 적는다. 결과 파일만 봐도 공정한 비교였는지 판단된다.
    private static String run(String format, String setting, double ssim, int concurrency,
                              List<BufferedImage> images, Encoder encoder) throws Exception {
        encodeAll(images.subList(0, Math.min(4, images.size())), concurrency, encoder);

        List<Double> wallMillis = new ArrayList<>();
        List<Double> throughputs = new ArrayList<>();
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            long start = System.nanoTime();
            encodeAll(images, concurrency, encoder);
            double elapsed = (System.nanoTime() - start) / 1_000_000.0;
            wallMillis.add(elapsed);
            throughputs.add(images.size() * 1000.0 / elapsed);
        }
        double meanWall = mean(wallMillis);
        double meanThroughput = mean(throughputs);
        System.out.printf("  %s 동시 %d: %.0f±%.0fms, %.1f±%.1f장/초%n", format, concurrency,
            meanWall, stdev(wallMillis), meanThroughput, stdev(throughputs));
        return String.format(Locale.ROOT, "%s,%s,%.4f,%d,%d,%d,%.1f,%.1f,%.2f,%.2f",
            format, setting, ssim, concurrency, images.size(), REPEATS,
            meanWall, stdev(wallMillis), meanThroughput, stdev(throughputs));
    }

    // 한 장만 재면 그 사진이 유난히 잘 눌리는 쪽이었을 수 있어 전부 재서 평균을 낸다.
    // 보고는 논문 정의인 가우시안 창으로 한다.
    private static double meanSsim(List<BufferedImage> images, Roundtrip roundtrip)
        throws Exception {
        List<Double> scores = new ArrayList<>();
        for (BufferedImage image : images) {
            scores.add(Ssim.between(image, roundtrip.apply(image), Ssim.Window.GAUSSIAN));
        }
        return mean(scores);
    }

    private static int requiredInt(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "-P" + key + " 를 줘야 한다. 품질 지점은 DerivativeFormatBenchmark 결과에서 고른다.");
        }
        return Integer.parseInt(value.trim());
    }

    private static String format(String pattern, Object... arguments) {
        return String.format(Locale.ROOT, pattern, arguments);
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    // 표본표준편차(n-1). 회차가 다섯이라 모표준편차를 쓰면 흔들림을 실제보다 작게 본다.
    private static double stdev(List<Double> values) {
        double average = mean(values);
        double sumOfSquares = values.stream()
            .mapToDouble(value -> (value - average) * (value - average))
            .sum();
        return Math.sqrt(sumOfSquares / (values.size() - 1));
    }

    private static void encodeAll(List<BufferedImage> images, int concurrency, Encoder encoder)
        throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            List<Future<byte[]>> futures = new ArrayList<>();
            for (BufferedImage image : images) {
                futures.add(pool.submit(() -> encoder.encode(image)));
            }
            for (Future<byte[]> future : futures) {
                future.get();
            }
        }
    }

    @FunctionalInterface
    private interface Encoder {
        byte[] encode(BufferedImage image) throws IOException;
    }

    // 인코딩한 뒤 되읽어 원본과 견주기 위한 왕복.
    @FunctionalInterface
    private interface Roundtrip {
        BufferedImage apply(BufferedImage image) throws IOException;
    }
}
