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
import net.coobird.thumbnailator.Thumbnails;

// 한 장씩 재는 DerivativeFormatBenchmark 만으로는 포맷을 고를 수 없다.
//
// AVIF 인코더(SVT-AV1)는 안에서 코어를 여러 개 쓰고 WebP 인코더는 한 코어만 쓴다. 그래서 한가한
// 장비에서 한 장만 재면 둘의 시간이 비슷하게 나오는데, 실제 워커 풀은 spring.task.execution 의
// core-size(4)만큼 동시에 돈다. 그 상태에서도 같은지를 봐야 업로드가 몰릴 때 무슨 일이 생기는지 안다.
//
//   ./gradlew derivativeConcurrencyBenchmark -Psamples=<샘플 디렉터리> -Pout=<결과 CSV>
public final class DerivativeConcurrencyBenchmark {

    private static final int TARGET_WIDTH = 1600;
    private static final float WEBP_QUALITY = 0.85f;
    private static final int AVIF_CRF = 28;

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
                scaled.add(DerivativeCodec.toRgb(Thumbnails.of(new ByteArrayInputStream(source))
                    .width(TARGET_WIDTH).keepAspectRatio(true).asBufferedImage()));
            }
        }
        System.out.printf("샘플 %d장을 %dpx 로 줄여 놓고 시작한다%n", scaled.size(), TARGET_WIDTH);

        List<String> rows = new ArrayList<>();
        rows.add("format,concurrency,images,repeats,meanWallMillis,stdevWallMillis,"
            + "meanImagesPerSecond,stdevImagesPerSecond");
        for (int concurrency : CONCURRENCIES) {
            rows.add(run("WEBP", concurrency, scaled,
                image -> DerivativeCodec.encodeWebp(image, WEBP_QUALITY)));
            rows.add(run("AVIF", concurrency, scaled,
                image -> DerivativeCodec.encodeAvif(image, AVIF_CRF)));
        }
        Files.writeString(output, String.join("\n", rows) + "\n");
        System.out.println("결과: " + output.toAbsolutePath());
    }

    private static String run(String format, int concurrency, List<BufferedImage> images,
                              Encoder encoder) throws Exception {
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
        return String.format(Locale.ROOT, "%s,%d,%d,%d,%.1f,%.1f,%.2f,%.2f",
            format, concurrency, images.size(), REPEATS,
            meanWall, stdev(wallMillis), meanThroughput, stdev(throughputs));
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
}
