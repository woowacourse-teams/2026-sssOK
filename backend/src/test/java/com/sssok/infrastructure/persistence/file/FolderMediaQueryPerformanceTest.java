package com.sssok.infrastructure.persistence.file;

import com.sssok.support.PostgresContainerSupport;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MEDIA_QUERY_BENCHMARK", matches = "true")
class FolderMediaQueryPerformanceTest extends PostgresContainerSupport {

    private static final long ROOM_ID = 1L;
    private static final int PAGE_LIMIT = 31;
    private static final int WARM_UP_COUNT = 2;
    private static final int MEASUREMENT_COUNT = 5;
    private static final Instant DEEP_CURSOR_AT = Instant.parse("2026-01-01T13:53:20Z");
    private static final long DEEP_CURSOR_ID = 50_000L;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void 안내() {
        System.out.println("폴더 미디어 쿼리 벤치마크: 100,000건, PostgreSQL 16");
    }

    @Test
    void 기존_IN_JOIN_EXISTS의_실행_시간을_비교한다() {
        prepareData();

        benchmark("희소 폴더 1% / 첫 페이지", 1L, null, null);
        benchmark("희소 폴더 1% / 깊은 커서", 1L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
        benchmark("일반 폴더 50% / 첫 페이지", 2L, null, null);
        benchmark("일반 폴더 50% / 깊은 커서", 2L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
        benchmark("밀집 폴더 90% / 첫 페이지", 3L, null, null);
        benchmark("밀집 폴더 90% / 깊은 커서", 3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
    }

    @Test
    void 기존_IN_JOIN_EXISTS의_실행_계획을_비교한다() {
        prepareData();

        printPlans("희소 폴더 1% / 첫 페이지", 1L, null, null);
        printPlans("밀집 폴더 90% / 깊은 커서", 3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
    }

    @Test
    void 정렬_복합_부분_인덱스_적용_전후를_비교한다() {
        prepareData();

        System.out.println("\n=== 기존 room_id 단일 인덱스 ===");
        benchmarkJoinAndExists("희소 폴더 1% / 첫 페이지", 1L, null, null);
        benchmarkJoinAndExists("희소 폴더 1% / 깊은 커서", 1L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
        benchmarkJoinAndExists("밀집 폴더 90% / 첫 페이지", 3L, null, null);
        benchmarkJoinAndExists("밀집 폴더 90% / 깊은 커서", 3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);

        createCursorPaginationIndex();

        System.out.println("\n=== room_id + created_at + id 부분 인덱스 ===");
        benchmarkJoinAndExists("희소 폴더 1% / 첫 페이지", 1L, null, null);
        benchmarkJoinAndExists("희소 폴더 1% / 깊은 커서", 1L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
        benchmarkJoinAndExists("밀집 폴더 90% / 첫 페이지", 3L, null, null);
        benchmarkJoinAndExists("밀집 폴더 90% / 깊은 커서", 3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);

        printSingleStatementPlans("희소 폴더 1% / 첫 페이지", 1L, null, null);
        printSingleStatementPlans("밀집 폴더 90% / 깊은 커서", 3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID);
    }

    @Test
    void 깊은_커서의_OR_조건과_튜플_조건을_비교한다() {
        prepareData();
        createCursorPaginationIndex();

        long orMedian = medianMillis(() -> queryWithJoin(3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID));
        long tupleMedian = medianMillis(() -> queryWithTupleCursorJoin(
            3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID));

        System.out.printf("""
            밀집 폴더 90%% / 깊은 커서
              OR 조건: %d ms
              튜플 조건: %d ms%n
            """, orMedian, tupleMedian);

        System.out.println("[OR 커서 조건]");
        printPlan(explainSingleStatement("""
            SELECT sf.id FROM benchmark_folder_media fm
            JOIN benchmark_stored_file sf ON sf.id = fm.media_id
            WHERE fm.folder_id = ? AND sf.room_id = ?
              AND sf.status IN ('PROCESSING', 'READY')
            """, 3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID));

        System.out.println("[튜플 커서 조건]");
        printPlan(explainTupleCursorJoin(3L, DEEP_CURSOR_AT, DEEP_CURSOR_ID));
    }

    private void prepareData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS benchmark_folder_media");
        jdbcTemplate.execute("DROP TABLE IF EXISTS benchmark_stored_file");
        jdbcTemplate.execute("""
            CREATE TABLE benchmark_stored_file (
                id BIGINT PRIMARY KEY,
                room_id BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL,
                created_at TIMESTAMPTZ NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE benchmark_folder_media (
                folder_id BIGINT NOT NULL,
                media_id BIGINT NOT NULL,
                CONSTRAINT uk_benchmark_folder_media UNIQUE (folder_id, media_id)
            )
            """);
        jdbcTemplate.execute("""
            INSERT INTO benchmark_stored_file (id, room_id, status, created_at)
            SELECT id, 1, 'READY', TIMESTAMPTZ '2026-01-01 00:00:00Z' + id * INTERVAL '1 second'
            FROM generate_series(1, 100000) AS id
            """);
        jdbcTemplate.execute("""
            INSERT INTO benchmark_folder_media (folder_id, media_id)
            SELECT 1, id FROM generate_series(1, 100000) AS id WHERE id % 100 = 0
            UNION ALL
            SELECT 2, id FROM generate_series(1, 100000) AS id WHERE id % 2 = 0
            UNION ALL
            SELECT 3, id FROM generate_series(1, 100000) AS id WHERE id % 10 <> 0
            """);
        jdbcTemplate.execute("CREATE INDEX idx_benchmark_stored_file_room_id ON benchmark_stored_file (room_id)");
        jdbcTemplate.execute("ANALYZE benchmark_stored_file");
        jdbcTemplate.execute("ANALYZE benchmark_folder_media");
    }

    private void benchmark(String scenario, Long folderId, Instant cursorAt, Long cursorId) {
        long inMedian = medianMillis(() -> queryWithIn(folderId, cursorAt, cursorId));
        long joinMedian = medianMillis(() -> queryWithJoin(folderId, cursorAt, cursorId));
        long existsMedian = medianMillis(() -> queryWithExists(folderId, cursorAt, cursorId));

        System.out.printf("%s%n  IN: %d ms%n  JOIN: %d ms%n  EXISTS: %d ms%n",
            scenario, inMedian, joinMedian, existsMedian);
    }

    private void benchmarkJoinAndExists(
        String scenario, Long folderId, Instant cursorAt, Long cursorId) {
        long joinMedian = medianMillis(() -> queryWithJoin(folderId, cursorAt, cursorId));
        long existsMedian = medianMillis(() -> queryWithExists(folderId, cursorAt, cursorId));

        System.out.printf("%s%n  JOIN: %d ms%n  EXISTS: %d ms%n",
            scenario, joinMedian, existsMedian);
    }

    private void createCursorPaginationIndex() {
        jdbcTemplate.execute("""
            CREATE INDEX idx_benchmark_stored_file_room_created_id_visible
            ON benchmark_stored_file (room_id, created_at DESC, id DESC)
            WHERE status IN ('PROCESSING', 'READY')
            """);
        jdbcTemplate.execute("ANALYZE benchmark_stored_file");
    }

    private void printPlans(String scenario, Long folderId, Instant cursorAt, Long cursorId) {
        System.out.printf("%n=== %s ===%n", scenario);
        System.out.println("[IN 1단계: 폴더의 전체 mediaId 조회]");
        printPlan(jdbcTemplate.queryForList("""
            EXPLAIN (ANALYZE, BUFFERS)
            SELECT media_id FROM benchmark_folder_media WHERE folder_id = ?
            """, String.class, folderId));

        List<Long> mediaIds = jdbcTemplate.queryForList(
            "SELECT media_id FROM benchmark_folder_media WHERE folder_id = ?",
            Long.class, folderId);
        System.out.println("[IN 2단계: mediaId 배열로 페이지 조회]");
        printPlan(explainWithIn(mediaIds, cursorAt, cursorId));

        System.out.println("[JOIN]");
        printPlan(explainSingleStatement("""
            SELECT sf.id FROM benchmark_folder_media fm
            JOIN benchmark_stored_file sf ON sf.id = fm.media_id
            WHERE fm.folder_id = ? AND sf.room_id = ?
              AND sf.status IN ('PROCESSING', 'READY')
            """, folderId, cursorAt, cursorId));

        System.out.println("[EXISTS]");
        printPlan(explainSingleStatement("""
            SELECT sf.id FROM benchmark_stored_file sf
            WHERE sf.room_id = ? AND sf.status IN ('PROCESSING', 'READY')
              AND EXISTS (
                SELECT 1 FROM benchmark_folder_media fm
                WHERE fm.folder_id = ? AND fm.media_id = sf.id
              )
            """, folderId, cursorAt, cursorId));
    }

    private void printSingleStatementPlans(
        String scenario, Long folderId, Instant cursorAt, Long cursorId) {
        System.out.printf("%n=== 인덱스 적용 실행 계획: %s ===%n", scenario);
        System.out.println("[JOIN]");
        printPlan(explainSingleStatement("""
            SELECT sf.id FROM benchmark_folder_media fm
            JOIN benchmark_stored_file sf ON sf.id = fm.media_id
            WHERE fm.folder_id = ? AND sf.room_id = ?
              AND sf.status IN ('PROCESSING', 'READY')
            """, folderId, cursorAt, cursorId));

        System.out.println("[EXISTS]");
        printPlan(explainSingleStatement("""
            SELECT sf.id FROM benchmark_stored_file sf
            WHERE sf.room_id = ? AND sf.status IN ('PROCESSING', 'READY')
              AND EXISTS (
                SELECT 1 FROM benchmark_folder_media fm
                WHERE fm.folder_id = ? AND fm.media_id = sf.id
              )
            """, folderId, cursorAt, cursorId));
    }

    private List<String> explainWithIn(List<Long> mediaIds, Instant cursorAt, Long cursorId) {
        return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            String cursorCondition = cursorAt == null ? "" : """
                 AND (created_at < ? OR (created_at = ? AND id < ?))
                """;
            String sql = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT id FROM benchmark_stored_file
                WHERE room_id = ? AND status IN ('PROCESSING', 'READY')
                  AND id = ANY (?)
                """ + cursorCondition + " ORDER BY created_at DESC, id DESC LIMIT ?";
            Array ids = connection.createArrayOf("bigint", mediaIds.toArray());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setLong(index++, ROOM_ID);
                statement.setArray(index++, ids);
                index = bindCursor(statement, index, cursorAt, cursorId);
                statement.setInt(index, PAGE_LIMIT);
                return readPlan(statement);
            } finally {
                ids.free();
            }
        });
    }

    private List<String> explainSingleStatement(
        String prefix, Long folderId, Instant cursorAt, Long cursorId) {
        return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            String cursorCondition = cursorAt == null ? "" : """
                 AND (sf.created_at < ? OR (sf.created_at = ? AND sf.id < ?))
                """;
            String sql = "EXPLAIN (ANALYZE, BUFFERS) " + prefix + cursorCondition
                + " ORDER BY sf.created_at DESC, sf.id DESC LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (prefix.contains("JOIN")) {
                    statement.setLong(index++, folderId);
                    statement.setLong(index++, ROOM_ID);
                } else {
                    statement.setLong(index++, ROOM_ID);
                    statement.setLong(index++, folderId);
                }
                index = bindCursor(statement, index, cursorAt, cursorId);
                statement.setInt(index, PAGE_LIMIT);
                return readPlan(statement);
            }
        });
    }

    private List<String> readPlan(PreparedStatement statement) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                lines.add(resultSet.getString(1));
            }
        }
        return lines;
    }

    private void printPlan(List<String> lines) {
        lines.forEach(line -> System.out.println("  " + line));
    }

    private long medianMillis(Runnable query) {
        for (int i = 0; i < WARM_UP_COUNT; i++) {
            query.run();
        }
        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_COUNT; i++) {
            long startedAt = System.nanoTime();
            query.run();
            elapsedNanos.add(System.nanoTime() - startedAt);
        }
        Collections.sort(elapsedNanos);
        return elapsedNanos.get(elapsedNanos.size() / 2) / 1_000_000;
    }

    private void queryWithIn(Long folderId, Instant cursorAt, Long cursorId) {
        List<Long> mediaIds = jdbcTemplate.queryForList(
            "SELECT media_id FROM benchmark_folder_media WHERE folder_id = ?",
            Long.class, folderId);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            String cursorCondition = cursorAt == null ? "" : """
                 AND (created_at < ? OR (created_at = ? AND id < ?))
                """;
            String sql = """
                SELECT id FROM benchmark_stored_file
                WHERE room_id = ? AND status IN ('PROCESSING', 'READY')
                  AND id = ANY (?)
                """ + cursorCondition + " ORDER BY created_at DESC, id DESC LIMIT ?";
            Array ids = connection.createArrayOf("bigint", mediaIds.toArray());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setLong(index++, ROOM_ID);
                statement.setArray(index++, ids);
                index = bindCursor(statement, index, cursorAt, cursorId);
                statement.setInt(index, PAGE_LIMIT);
                consume(statement);
            } finally {
                ids.free();
            }
            return null;
        });
    }

    private void queryWithJoin(Long folderId, Instant cursorAt, Long cursorId) {
        querySingleStatement("""
            SELECT sf.id FROM benchmark_folder_media fm
            JOIN benchmark_stored_file sf ON sf.id = fm.media_id
            WHERE fm.folder_id = ? AND sf.room_id = ?
              AND sf.status IN ('PROCESSING', 'READY')
            """, folderId, cursorAt, cursorId);
    }

    private void queryWithExists(Long folderId, Instant cursorAt, Long cursorId) {
        querySingleStatement("""
            SELECT sf.id FROM benchmark_stored_file sf
            WHERE sf.room_id = ? AND sf.status IN ('PROCESSING', 'READY')
              AND EXISTS (
                SELECT 1 FROM benchmark_folder_media fm
                WHERE fm.folder_id = ? AND fm.media_id = sf.id
              )
            """, folderId, cursorAt, cursorId);
    }

    private void queryWithTupleCursorJoin(
        Long folderId, Instant cursorAt, Long cursorId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            String sql = """
                SELECT sf.id FROM benchmark_folder_media fm
                JOIN benchmark_stored_file sf ON sf.id = fm.media_id
                WHERE fm.folder_id = ? AND sf.room_id = ?
                  AND sf.status IN ('PROCESSING', 'READY')
                  AND (sf.created_at, sf.id) < (?, ?)
                ORDER BY sf.created_at DESC, sf.id DESC
                LIMIT ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, folderId);
                statement.setLong(2, ROOM_ID);
                statement.setTimestamp(3, Timestamp.from(cursorAt));
                statement.setLong(4, cursorId);
                statement.setInt(5, PAGE_LIMIT);
                consume(statement);
            }
            return null;
        });
    }

    private List<String> explainTupleCursorJoin(
        Long folderId, Instant cursorAt, Long cursorId) {
        return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            String sql = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT sf.id FROM benchmark_folder_media fm
                JOIN benchmark_stored_file sf ON sf.id = fm.media_id
                WHERE fm.folder_id = ? AND sf.room_id = ?
                  AND sf.status IN ('PROCESSING', 'READY')
                  AND (sf.created_at, sf.id) < (?, ?)
                ORDER BY sf.created_at DESC, sf.id DESC
                LIMIT ?
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, folderId);
                statement.setLong(2, ROOM_ID);
                statement.setTimestamp(3, Timestamp.from(cursorAt));
                statement.setLong(4, cursorId);
                statement.setInt(5, PAGE_LIMIT);
                return readPlan(statement);
            }
        });
    }

    private void querySingleStatement(String prefix, Long folderId, Instant cursorAt, Long cursorId) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            String cursorCondition = cursorAt == null ? "" : """
                 AND (sf.created_at < ? OR (sf.created_at = ? AND sf.id < ?))
                """;
            String sql = prefix + cursorCondition + " ORDER BY sf.created_at DESC, sf.id DESC LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (prefix.contains("JOIN")) {
                    statement.setLong(index++, folderId);
                    statement.setLong(index++, ROOM_ID);
                } else {
                    statement.setLong(index++, ROOM_ID);
                    statement.setLong(index++, folderId);
                }
                index = bindCursor(statement, index, cursorAt, cursorId);
                statement.setInt(index, PAGE_LIMIT);
                consume(statement);
            }
            return null;
        });
    }

    private int bindCursor(PreparedStatement statement, int index, Instant cursorAt, Long cursorId)
        throws SQLException {
        if (cursorAt == null) {
            return index;
        }
        Timestamp timestamp = Timestamp.from(cursorAt);
        statement.setTimestamp(index++, timestamp);
        statement.setTimestamp(index++, timestamp);
        statement.setLong(index++, cursorId);
        return index;
    }

    private void consume(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                resultSet.getLong(1);
            }
        }
    }
}
