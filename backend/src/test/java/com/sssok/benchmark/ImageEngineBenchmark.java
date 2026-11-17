package com.sssok.benchmark;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.renewal.outgoing.processing.image.*;
import com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessor.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.Duration;

/** 원본을 미리 읽고 가공 경계를 비교한다. CLI 임시 쓰기·프로세스·읽기·삭제도 포함한다. */
public class ImageEngineBenchmark {
    public static void main(String[] args) throws Exception {
        String engine = args[0];
        int concurrency = Integer.parseInt(args[1]);
        int warmups = args.length > 4 ? Integer.parseInt(args[4]) : 1;
        int repeats = args.length > 5 ? Integer.parseInt(args[5]) : 3;
        int batchSize = args.length > 6 ? Integer.parseInt(args[6]) : 20;
        int warmupBatchSize = args.length > 7 ? Integer.parseInt(args[7]) : batchSize;
        if (concurrency < 1 || warmups < 0 || repeats < 1 || batchSize < 1 || warmupBatchSize < 1) {
            throw new IllegalArgumentException("Invalid benchmark dimensions");
        }
        Path input = Path.of(args[2]), output = Path.of(args[3]);
        Files.createDirectories(output);
        var operations = new ImageDerivativeOperations();
        Optional<ImageDerivativeEngine> selected = switch (engine) {
            case "ffm" -> Optional.of((ImageDerivativeEngine) Class.forName(
                "com.sssok.renewal.outgoing.processing.image.LibvipsFfmImageProcessor")
                .getConstructor().newInstance());
            case "jni" -> Optional.of(new LibvipsImageProcessor());
            case "cli" -> Optional.of(new LibvipsCliImageProcessor(operations, "vips", Duration.ofSeconds(20)));
            case "java" -> Optional.empty();
            default -> throw new IllegalArgumentException(engine);
        };
        var processor = new ThumbnailatorImageProcessor(operations, selected);
        var thumbnail = new DerivativeSpec(400, DerivativeFormat.WEBP, .80f);
        var preview = new DerivativeSpec(1600, DerivativeFormat.WEBP, .85f);
        var inputs = new ArrayList<byte[]>();
        try (var paths = Files.list(input)) {
            for (var p : paths.filter(p -> p.toString().endsWith(".jpg")).sorted().toList()) inputs.add(Files.readAllBytes(p));
        }
        if (inputs.size() != 6) throw new IllegalArgumentException("Expected six source JPEGs");
        try (var pool = Executors.newFixedThreadPool(concurrency);
             var rows = Files.newBufferedWriter(output.resolve("rows.csv"));
             var batches = Files.newBufferedWriter(output.resolve("batches.csv"))) {
            rows.write("repeat,index,total_ms,thumbnail_bytes,preview_bytes,queue_ms,completion_ms\n");
            batches.write("repeat,wall_ms,batch_size,memory_current_bytes,heap_used_bytes\n");
            for (int repeat = -warmups; repeat < repeats; repeat++) {
                int rep = repeat;
                long start = System.nanoTime();
                var futures = new ArrayList<Future<String>>();
                int count = repeat < 0 ? warmupBatchSize : batchSize;
                for (int i = 0; i < count; i++) {
                    int index = i;
                    long submitted = System.nanoTime();
                    futures.add(pool.submit(() -> {
                        long before = System.nanoTime();
                        var result = processor.derive(inputs.get(index % inputs.size()), thumbnail, preview).orElseThrow();
                        long finished = System.nanoTime();
                        double ms = (finished - before) / 1e6;
                        return String.format(Locale.ROOT, "%d,%d,%.4f,%d,%d,%.4f,%.4f%n", rep, index, ms,
                            result.thumbnail().content().length, result.preview().content().length,
                            (before - submitted) / 1e6, (finished - start) / 1e6);
                    }));
                }
                for (var future : futures) {
                    String row = future.get();
                    if (repeat >= 0) rows.write(row);
                }
                double ms = (System.nanoTime() - start) / 1e6;
                if (repeat >= 0) {
                    var memory = Path.of("/sys/fs/cgroup/memory.current");
                    long current = Files.exists(memory) ? Long.parseLong(Files.readString(memory).trim()) : -1;
                    var runtime = Runtime.getRuntime();
                    batches.write(String.format(Locale.ROOT, "%d,%.4f,%d,%d,%d%n", repeat, ms, count,
                        current, runtime.totalMemory() - runtime.freeMemory()));
                }
                rows.flush(); batches.flush();
                System.out.printf(Locale.ROOT, "%s c%d n%d repeat%d %.1fms%n", engine, concurrency, count, repeat, ms);
            }
        }
        // 비교 이미지 저장은 시간 측정 후 수행한다.
        for (int i = 0; i < inputs.size(); i++) {
            var result = processor.derive(inputs.get(i), thumbnail, preview).orElseThrow();
            Files.write(output.resolve(i + "-thumbnail.webp"), result.thumbnail().content());
            Files.write(output.resolve(i + "-preview.webp"), result.preview().content());
        }
    }
}
