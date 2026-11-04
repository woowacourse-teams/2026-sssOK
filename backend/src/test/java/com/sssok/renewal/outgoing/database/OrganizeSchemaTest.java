package com.sssok.renewal.outgoing.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

// validate만으로는 FK·CHECK·인덱스가 검증되지 않으므로 실제 DB 제약과 거절 동작도 확인한다.
@PostgresIntegrationTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class OrganizeSchemaTest extends PostgresContainerSupport {
    @Autowired JdbcTemplate jdbc;

    private static final List<Class<?>> ENTITIES = List.of(
        OrganizeJobJpaEntity.class, OrganizeExecutionJpaEntity.class, OrganizeJobPhotoJpaEntity.class,
        OrganizeTrialJpaEntity.class, OrganizeIdempotencyJpaEntity.class,
        OrganizeStartGateJpaEntity.class, OrganizeRoomStateJpaEntity.class);

    private long job() {
        return jdbc.queryForObject("""
            INSERT INTO organize_job(public_id, room_id, requester_id, kind, trigger_type, status,
                server_id, tool_name, tool_description, photo_count, excluded_count, created_at, updated_at)
            VALUES (?, 900001, 900001, 'PERSON', 'BUTTON', 'FAILED', 'gallery', 'tool', 'description',
                1, 0, now(), now()) RETURNING id
            """, Long.class, UUID.randomUUID());
    }

    private void child(String table, long jobId) {
        switch (table) {
            case "organize_execution" -> jdbc.update("""
                INSERT INTO organize_execution(job_id, execution_id, attempt, status, dispatch_attempts,
                    dispatch_deadline_at, created_at, updated_at)
                VALUES (?, ?, 1, 'DISPATCH_PENDING', 0, now(), now(), now())
                """, jobId, UUID.randomUUID());
            case "organize_job_photo" -> jdbc.update("""
                INSERT INTO organize_job_photo(job_id, media_id, extension, source, created_at, updated_at)
                VALUES (?, 900001, 'jpg', 'PREVIEW', now(), now())
                """, jobId);
            case "organize_trial" -> jdbc.update("""
                INSERT INTO organize_trial(job_id, member_id, room_id, created_at, updated_at)
                VALUES (?, ?, 900001, now(), now())
                """, jobId, jobId);
            case "organize_idempotency" -> jdbc.update("""
                INSERT INTO organize_idempotency(job_id, member_id, room_id, operation, idem_key,
                    request_hash, created_at, updated_at)
                VALUES (?, 900001, 900001, 'START', ?, ?, now(), now())
                """, jobId, UUID.randomUUID().toString(), "a".repeat(64));
            default -> throw new IllegalArgumentException(table);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"organize_execution", "organize_job_photo", "organize_trial", "organize_idempotency"})
    void 없는_작업을_참조하거나_참조_중인_작업을_삭제할_수_없다(String table) {
        assertThatThrownBy(() -> child(table, Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        long id = job();
        child(table, id);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM organize_job WHERE id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM " + table + " WHERE job_id = ?", id);
        assertThat(jdbc.update("DELETE FROM organize_job WHERE id = ?", id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status = 'UNKNOWN'", "kind = 'UNKNOWN'", "trigger_type = 'UNKNOWN'",
        "photo_count = 0", "excluded_count = -1", "job_version = 0", "retry_count = -1",
        "status = 'READY'", "active_room_id = 900001"})
    void 잘못된_작업_상태와_횟수와_점유를_거절한다(String assignment) {
        long id = job();
        assertThatThrownBy(() -> jdbc.update("UPDATE organize_job SET " + assignment + " WHERE id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "organize_execution | status = 'UNKNOWN'", "organize_execution | attempt = 0",
        "organize_execution | dispatch_attempts = -1", "organize_job_photo | source = 'UNKNOWN'",
        "organize_idempotency | operation = 'UNKNOWN'", "organize_idempotency | request_hash = 'short'"
    })
    void 실행과_사진과_멱등_기록도_잘못된_값을_거절한다(String table, String assignment) {
        long id = job();
        child(table, id);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET " + assignment + " WHERE job_id = ?", id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 방_상태_버전은_양수여야_한다() {
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO organize_room_state(room_id, version, busy, created_at, updated_at)
            VALUES (900001, 0, false, now(), now())
            """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 활성_방은_하나만_점유하고_종료_이력은_여러_개_남긴다() {
        long first = job();
        long second = job();
        jdbc.update("UPDATE organize_job SET status = 'QUEUED', active_room_id = room_id WHERE id = ?", first);
        assertThatThrownBy(() -> jdbc.update(
            "UPDATE organize_job SET status = 'QUEUED', active_room_id = room_id WHERE id = ?", second))
            .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE organize_job SET status = 'FAILED', active_room_id = NULL WHERE id = ?", first);
        assertThat(jdbc.update(
            "UPDATE organize_job SET status = 'QUEUED', active_room_id = room_id WHERE id = ?", second)).isEqualTo(1);
        jdbc.update("UPDATE organize_job SET status = 'FAILED', active_room_id = NULL WHERE id = ?", second);
    }

    @Test
    void 엔티티에_선언한_제약과_인덱스가_실제_테이블에도_존재한다() {
        for (Class<?> entity : ENTITIES) {
            Table table = entity.getAnnotation(Table.class);
            for (Check check : entity.getAnnotationsByType(Check.class)) {
                assertConstraint(table.name(), check.name(), "c");
            }
            for (var unique : table.uniqueConstraints()) {
                assertConstraint(table.name(), unique.name(), "u");
            }
            for (var index : table.indexes()) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename = ? AND indexname = ?",
                    Integer.class, table.name(), index.name())).isEqualTo(1);
            }
            for (var field : entity.getDeclaredFields()) {
                JoinColumn join = field.getAnnotation(JoinColumn.class);
                if (join != null) {
                    assertConstraint(table.name(), join.foreignKey().name(), "f");
                    assertThat(jdbc.queryForObject("SELECT confdeltype::text FROM pg_constraint WHERE conname = ?",
                        String.class, join.foreignKey().name())).isEqualTo("a");
                }
            }
        }
    }

    private void assertConstraint(String table, String name, String type) {
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_constraint
            WHERE conrelid = CAST(? AS regclass) AND conname = ? AND contype::text = ? AND convalidated
            """, Integer.class, table, name, type)).as(name).isEqualTo(1);
    }

    @Test
    void DB_기본값과_조건부_인덱스를_유지한다() {
        long id = job();
        assertThat(jdbc.queryForObject("SELECT job_version FROM organize_job WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT retry_count FROM organize_job WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM pg_indexes WHERE tablename = 'organize_job'
              AND indexname IN ('idx_organize_job_review_expires', 'idx_organize_job_result_expires')
              AND indexdef LIKE '%WHERE%'
            """, Integer.class)).isEqualTo(2);
    }
}
