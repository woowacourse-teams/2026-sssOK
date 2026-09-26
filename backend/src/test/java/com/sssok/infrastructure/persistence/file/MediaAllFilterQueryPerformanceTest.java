package com.sssok.infrastructure.persistence.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.support.PostgresContainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MEDIA_ALL_FILTER_BENCHMARK", matches = "true")
class MediaAllFilterQueryPerformanceTest extends PostgresContainerSupport {

    private static final long ROOM_ID = 304_000L;
    private static final long REQUESTER_ID = 304_001L;
    private static final long OTHER_UPLOADER_ID = 304_002L;
    private static final long FOLDER_ID = 304_003L;
    private static final int MEDIA_COUNT = 100_000;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 전체_목록_필터별_실행_계획과_인덱스를_확인한다() {
        prepareData();

        assertProductionIndexes();
        List<String> roomAll = explain(roomQuery(), "ALL");
        List<String> roomMe = explain(roomQuery(), "ME");
        List<String> roomOthers = explain(roomQuery(), "OTHERS");
        List<String> folderAll = explain(folderQuery(), "ALL");
        List<String> folderMe = explain(folderQuery(), "ME");
        List<String> folderOthers = explain(folderQuery(), "OTHERS");

        assertThat(joined(roomAll)).contains("idx_stored_file_room_created_id_visible");
        assertThat(joined(roomMe)).contains("idx_stored_file_room_uploader_created_id_visible");
        assertThat(joined(roomOthers)).contains("idx_stored_file_room_created_id_visible");
        assertThat(joined(folderAll)).contains("uk_folder_media");
        assertThat(joined(folderMe))
            .contains("uk_folder_media", "idx_stored_file_room_uploader_created_id_visible");
        assertThat(joined(folderOthers)).contains("uk_folder_media");

        printPlan("방 전체 / ALL", roomAll);
        printPlan("방 전체 / ME", roomMe);
        printPlan("방 전체 / OTHERS", roomOthers);
        printPlan("폴더 / ALL", folderAll);
        printPlan("폴더 / ME", folderMe);
        printPlan("폴더 / OTHERS", folderOthers);
    }

    private void prepareData() {
        jdbcTemplate.update("""
            DELETE FROM folder_media
            WHERE media_id IN (SELECT id FROM stored_file WHERE room_id = ?)
            """, ROOM_ID);
        jdbcTemplate.update("DELETE FROM stored_file WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("""
            INSERT INTO stored_file (
                room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                storage_key, status, created_at, updated_at, reserved_at, retry_count
            )
            SELECT ?,
                   CASE WHEN n % 10 = 0 THEN ? ELSE ? END,
                   'benchmark-' || n || '.jpg', 'JPEG', 1024,
                   'benchmark/media-all-filter/' || n, 'READY',
                   TIMESTAMPTZ '2026-01-01 00:00:00Z' + n * INTERVAL '1 second',
                   now(), now(), 0
            FROM generate_series(1, ?) AS n
            """, ROOM_ID, REQUESTER_ID, OTHER_UPLOADER_ID, MEDIA_COUNT);
        jdbcTemplate.update("""
            INSERT INTO folder_media (folder_id, media_id, created_at, updated_at)
            SELECT ?, id, now(), now()
            FROM stored_file
            WHERE room_id = ? AND id % 5 = 0
            """, FOLDER_ID, ROOM_ID);
        jdbcTemplate.update("""
            INSERT INTO folder_media (folder_id, media_id, created_at, updated_at)
            SELECT ?, id, now(), now()
            FROM stored_file
            WHERE room_id = ?
            """, FOLDER_ID + 1, ROOM_ID);
        jdbcTemplate.execute("ANALYZE stored_file");
        jdbcTemplate.execute("ANALYZE folder_media");
    }

    private void assertProductionIndexes() {
        List<String> indexNames = jdbcTemplate.queryForList("""
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND tablename IN ('stored_file', 'folder_media')
            """, String.class);

        assertThat(indexNames).contains(
            "idx_stored_file_room_created_id_visible",
            "idx_stored_file_room_uploader_created_id_visible",
            "uk_folder_media");
    }

    private List<String> explain(String query, String uploader) {
        return jdbcTemplate.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + query, String.class,
            ROOM_ID, uploader, uploader, REQUESTER_ID, uploader, REQUESTER_ID);
    }

    private String roomQuery() {
        return """
            SELECT sf.*
            FROM stored_file sf
            WHERE sf.room_id = ?
              AND sf.status IN ('PROCESSING', 'READY')
              AND (? = 'ALL'
                OR (? = 'ME' AND sf.uploader_id = ?)
                OR (? = 'OTHERS' AND sf.uploader_id <> ?))
            ORDER BY sf.created_at DESC, sf.id DESC
            """;
    }

    private String folderQuery() {
        return """
            SELECT sf.*
            FROM stored_file sf
            JOIN folder_media fm ON fm.media_id = sf.id
            WHERE sf.room_id = ?
              AND fm.folder_id = %d
              AND sf.status IN ('PROCESSING', 'READY')
              AND (? = 'ALL'
                OR (? = 'ME' AND sf.uploader_id = ?)
                OR (? = 'OTHERS' AND sf.uploader_id <> ?))
            ORDER BY sf.created_at DESC, sf.id DESC
            """.formatted(FOLDER_ID);
    }

    private void printPlan(String scenario, List<String> plan) {
        System.out.printf("%n=== %s ===%n", scenario);
        plan.forEach(System.out::println);
    }

    private String joined(List<String> plan) {
        return String.join("\n", plan);
    }
}
