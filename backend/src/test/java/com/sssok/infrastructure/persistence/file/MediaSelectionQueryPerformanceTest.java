package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.FileRepository;
import com.sssok.support.PostgresContainerSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.show-sql=false")
@EnabledIfEnvironmentVariable(named = "RUN_MEDIA_SELECTION_BENCHMARK", matches = "true")
class MediaSelectionQueryPerformanceTest extends PostgresContainerSupport {

    private static final long ROOM_ID = 1L;
    private static final int WARM_UP_COUNT = 5;
    private static final int MEASUREMENT_COUNT = 15;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FileRepository fileRepository;

    @BeforeAll
    static void 안내() {
        System.out.println("미디어 exclude 선택 벤치마크: 10,000건, PostgreSQL 16");
    }

    @Test
    void 전체_조회_후_메모리_제외와_DB_NOT_IN을_비교한다() {
        prepareData();

        benchmark(0);
        benchmark(10);
        benchmark(1_000);
        benchmark(9_000);
    }

    private void prepareData() {
        jdbcTemplate.execute("DELETE FROM folder_media");
        jdbcTemplate.execute("DELETE FROM stored_file");
        jdbcTemplate.execute("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            SELECT id, 1, 1, 'benchmark-photo.jpg', 'JPEG', 1048576,
                   'rooms/1/' || id || '.jpg', 'READY', now(), now(), now(), 0
            FROM generate_series(1, 10000) AS id
            """);
        jdbcTemplate.execute("""
            INSERT INTO stored_file
                (id, room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                 storage_key, status, created_at, updated_at, reserved_at, retry_count)
            SELECT id, 2, 1, 'other-room-photo.jpg', 'JPEG', 1048576,
                   'rooms/2/' || id || '.jpg', 'READY', now(), now(), now(), 0
            FROM generate_series(10001, 20000) AS id
            """);
        jdbcTemplate.execute("ANALYZE stored_file");
    }

    private void benchmark(int excludedCount) {
        List<Long> excludedIds = LongStream.rangeClosed(1, excludedCount).boxed().toList();
        Set<Long> excludedSet = new HashSet<>(excludedIds);
        Comparison comparison = compare(
            () -> legacyQuery(excludedSet), () -> databaseQuery(excludedIds));
        Measurement legacy = comparison.legacy();
        Measurement database = comparison.database();
        double changePercent = (database.nanos() - legacy.nanos()) * 100.0 / legacy.nanos();

        System.out.printf(
            "exclude %,5d건 | 결과 %,5d건 | 기존 %.3f ms | DB 제외 %.3f ms | 변화 %+.1f%%%n",
            excludedCount, database.resultCount(), nanosToMillis(legacy.nanos()),
            nanosToMillis(database.nanos()), changePercent);
    }

    private int legacyQuery(Set<Long> excludedIds) {
        return (int) fileRepository.findAllByRoomId(ROOM_ID).stream()
            .filter(file -> !excludedIds.contains(file.getId()))
            .count();
    }

    private int databaseQuery(List<Long> excludedIds) {
        return fileRepository.findAllByRoomIdAndIdNotIn(ROOM_ID, excludedIds).size();
    }

    private Comparison compare(Supplier<Integer> legacy, Supplier<Integer> database) {
        for (int i = 0; i < WARM_UP_COUNT; i++) {
            legacy.get();
            database.get();
        }
        List<Long> legacySamples = new ArrayList<>();
        List<Long> databaseSamples = new ArrayList<>();
        int legacyResultCount = 0;
        int databaseResultCount = 0;
        for (int i = 0; i < MEASUREMENT_COUNT; i++) {
            boolean databaseFirst = i % 2 == 0;
            if (databaseFirst) {
                databaseResultCount = measure(database, databaseSamples);
                legacyResultCount = measure(legacy, legacySamples);
            } else {
                legacyResultCount = measure(legacy, legacySamples);
                databaseResultCount = measure(database, databaseSamples);
            }
        }
        Collections.sort(legacySamples);
        Collections.sort(databaseSamples);
        return new Comparison(
            new Measurement(legacySamples.get(legacySamples.size() / 2), legacyResultCount),
            new Measurement(databaseSamples.get(databaseSamples.size() / 2), databaseResultCount));
    }

    private int measure(Supplier<Integer> operation, List<Long> samples) {
        long startedAt = System.nanoTime();
        int resultCount = operation.get();
        samples.add(System.nanoTime() - startedAt);
        return resultCount;
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record Measurement(long nanos, int resultCount) {
    }

    private record Comparison(Measurement legacy, Measurement database) {
    }
}
