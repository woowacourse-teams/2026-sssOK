package com.sssok.application.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.presentation.api.media.AllMediaListResponse;
import com.sssok.presentation.api.media.MediaListResponse;
import com.sssok.support.PostgresContainerSupport;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@EnabledIfEnvironmentVariable(named = "RUN_MEDIA_LIST_MODE_BENCHMARK", matches = "true")
class MediaListModePerformanceTest extends PostgresContainerSupport {

    private static final long ROOM_ID = 276_000L;
    private static final long UPLOADER_ID = 276_000L;
    private static final int PAGE_SIZE = 100;
    private static final int MEASUREMENT_COUNT = 5;

    @Autowired
    GetMediaListService getMediaListService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @BeforeEach
    void setUp() {
        given(fileStoragePort.presignGet(any(), anyString(), anyString(), any()))
            .willReturn("https://storage.example.com/signed");
        jdbcTemplate.update("DELETE FROM stored_file WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", UPLOADER_ID);
        jdbcTemplate.update(
            "INSERT INTO member (id, nickname, created_at, updated_at) VALUES (?, ?, now(), now())",
            UPLOADER_ID, "benchmark");
    }

    @Test
    void 데이터_규모별로_전체_조회와_페이지_순회를_비교한다() throws Exception {
        System.out.println("count,mode,median_ms,query_count,response_bytes,peak_items");
        for (int count : List.of(100, 1_000, 10_000)) {
            seed(count);

            // 첫 호출의 클래스 로딩·쿼리 파싱 비용이 결과를 지배하지 않도록 한 번 예열한다.
            measureAll(count);
            measureFirstPage(count);
            measureAllPages(count);

            print(median(count, "all", () -> measureAll(count)));
            print(median(count, "cursor-first", () -> measureFirstPage(count)));
            print(median(count, "cursor-all", () -> measureAllPages(count)));
        }
    }

    private Measurement measureAll(int count) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        long startedAt = System.nanoTime();
        List<MediaDetail> items = getMediaListService.list(ROOM_ID, null);
        byte[] response = objectMapper.writeValueAsBytes(AllMediaListResponse.from(items));
        long elapsedNanos = System.nanoTime() - startedAt;
        return new Measurement(count, "all", elapsedNanos, statistics.getPrepareStatementCount(),
            response.length, items.size());
    }

    private Measurement measureFirstPage(int count) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        long startedAt = System.nanoTime();
        MediaPage page = getMediaListService.page(
            ROOM_ID, null, UPLOADER_ID, MediaUploaderFilter.ALL, PAGE_SIZE, null);
        String nextCursor = page.nextCursor() == null ? null : "benchmark-cursor";
        byte[] response = objectMapper.writeValueAsBytes(
            MediaListResponse.from(page, nextCursor));
        long elapsedNanos = System.nanoTime() - startedAt;
        return new Measurement(count, "cursor-first", elapsedNanos,
            statistics.getPrepareStatementCount(), response.length, page.items().size());
    }

    private Measurement measureAllPages(int count) throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        long startedAt = System.nanoTime();
        long responseBytes = 0;
        int peakItems = 0;
        MediaCursor cursor = null;
        do {
            MediaPage page = getMediaListService.page(
                ROOM_ID, null, UPLOADER_ID, MediaUploaderFilter.ALL, PAGE_SIZE, cursor);
            String nextCursor = page.nextCursor() == null ? null : "benchmark-cursor";
            responseBytes += objectMapper.writeValueAsBytes(
                MediaListResponse.from(page, nextCursor)).length;
            peakItems = Math.max(peakItems, page.items().size());
            cursor = page.nextCursor();
        } while (cursor != null);
        long elapsedNanos = System.nanoTime() - startedAt;
        return new Measurement(count, "cursor-all", elapsedNanos,
            statistics.getPrepareStatementCount(), responseBytes, peakItems);
    }

    private Measurement median(int count, String mode, MeasurementSupplier supplier)
        throws Exception {
        List<Measurement> measurements = new ArrayList<>();
        for (int run = 0; run < MEASUREMENT_COUNT; run++) {
            measurements.add(supplier.get());
        }
        Measurement median = measurements.stream()
            .sorted(Comparator.comparingLong(Measurement::elapsedNanos))
            .toList()
            .get(MEASUREMENT_COUNT / 2);
        return new Measurement(count, mode, median.elapsedNanos(), median.queryCount(),
            median.responseBytes(), median.peakItems());
    }

    private void seed(int count) {
        jdbcTemplate.update("DELETE FROM stored_file WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("""
            INSERT INTO stored_file (
                room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                storage_key, status, created_at, updated_at, reserved_at, retry_count
            )
            SELECT ?, ?, 'image-' || n || '.jpg', 'JPEG', 1024,
                   'benchmark/' || ? || '/' || n, 'READY',
                   now() - n * interval '1 millisecond', now(), now(), 0
            FROM generate_series(1, ?) AS n
            """, ROOM_ID, UPLOADER_ID, count, count);
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private void print(Measurement measurement) {
        System.out.printf("%d,%s,%.3f,%d,%d,%d%n",
            measurement.count(), measurement.mode(), measurement.elapsedNanos() / 1_000_000.0,
            measurement.queryCount(), measurement.responseBytes(), measurement.peakItems());
    }

    private record Measurement(
        int count,
        String mode,
        long elapsedNanos,
        long queryCount,
        long responseBytes,
        int peakItems
    ) {
    }

    @FunctionalInterface
    private interface MeasurementSupplier {

        Measurement get() throws Exception;
    }
}
