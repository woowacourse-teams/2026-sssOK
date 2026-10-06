package com.sssok.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.search.ImageAnalysis;
import com.sssok.infrastructure.persistence.search.MediaSearchDocumentRepositoryAdapter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.boot.test.context.SpringBootTest
class MediaSearchDocumentRepositoryTest extends PostgresContainerSupport {
    private Instant now;
    @org.springframework.beans.factory.annotation.Autowired
    private JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired
    private MediaSearchDocumentRepositoryAdapter repository;

    @BeforeEach
    void setup() {
        cleanup();
        insert(460001L, "JPEG", "READY");
        now = Instant.now().plusSeconds(1);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stored_file WHERE id IN (460001, 460002, 460003)");
    }

    @Test
    void 준비된_이미지만_등록하고_중복_등록은_무시한다() {
        insert(460002L, "MP4", "READY");
        insert(460003L, "JPEG", "PROCESSING");
        repository.register(460001L);
        repository.register(460001L);
        repository.register(460002L);
        repository.register(460003L);
        repository.register(999L);
        assertThat(repository.findPending(now, 10)).containsExactly(460001L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_search_document WHERE media_id = 460001", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void 누락된_등록은_도입_시각과_배치_상한을_지킨다() {
        insert(460002L, "PNG", "READY");
        insert(460003L, "MP4", "READY");
        assertThat(repository.registerMissing(now.plusSeconds(3600), 10)).isZero();
        assertThat(repository.registerMissing(now.minusSeconds(60), 1)).isEqualTo(1);
        assertThat(repository.registerMissing(now.minusSeconds(60), 10)).isEqualTo(1);
        assertThat(repository.registerMissing(now.minusSeconds(60), 10)).isZero();
        assertThat(repository.findPending(now, 10)).containsExactly(460001L, 460002L);
    }

    @Test
    void 동시에_실행해도_한_워커만_선점한다() throws Exception {
        repository.register(460001L);
        Callable<Boolean> claim = () -> repository.claim(460001L, now, 3).isPresent();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(claim, claim));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void 작업은_한번만_선점하고_분석_결과와_벡터를_저장한다() {
        repository.register(460001L);
        var attempt = repository.claim(460001L, now, 3).orElseThrow();
        assertThat(repository.claim(460001L, now, 3)).isEmpty();
        assertThat(repository.complete(attempt, analysis(), now)).isTrue();
        assertThat(repository.complete(attempt, analysis(), now)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM media_search_document WHERE media_id = 460001", String.class))
            .isEqualTo("READY");
        assertThat(jdbc.queryForObject(
            "SELECT vector_dims(embedding) FROM media_search_document WHERE media_id = 460001", Integer.class)).isEqualTo(3);
        assertThat(repository.findPending(now, 10)).isEmpty();
        var storedVector = entityManager.createNativeQuery(
            "SELECT CAST(embedding AS text) FROM media_search_document WHERE media_id = 460001")
            .getSingleResult();
        assertThat(storedVector).isEqualTo("[1,0,0]");
    }

    @Test
    void 재시도_시각과_최대_시도_횟수를_지킨다() {
        repository.register(460001L);
        var first = repository.claim(460001L, now, 2).orElseThrow();
        assertThat(repository.fail(first, "AI_TIMEOUT", now.plusSeconds(60), 2)).isTrue();
        assertThat(repository.claim(460001L, now, 2)).isEmpty();
        assertThat(repository.findPending(now, 10)).isEmpty();
        var second = repository.claim(460001L, now.plusSeconds(60), 2).orElseThrow();
        assertThat(repository.fail(second, "AI_TIMEOUT", now.plusSeconds(120), 2)).isTrue();
        assertThat(repository.claim(460001L, now.plusSeconds(120), 2)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM media_search_document WHERE media_id = 460001", String.class))
            .isEqualTo("FAILED");
    }

    @Test
    void 중단된_작업을_복구하면_이전_워커의_늦은_결과는_무시한다() {
        repository.register(460001L);
        var oldAttempt = repository.claim(460001L, now, 2).orElseThrow();
        assertThat(repository.recover(now.minusSeconds(1), now, 2)).isZero();
        assertThat(repository.recover(now.plusSeconds(1), now.plusSeconds(10), 2)).isEqualTo(1);
        var newAttempt = repository.claim(460001L, now.plusSeconds(10), 2).orElseThrow();
        assertThat(repository.complete(oldAttempt, analysis(), now)).isFalse();
        assertThat(repository.fail(oldAttempt, "LATE_ERROR", now, 2)).isFalse();
        assertThat(repository.complete(newAttempt, analysis(), now.plusSeconds(11))).isTrue();
    }

    @Test
    void 마지막_시도의_중단은_실패로_끝낸다() {
        repository.register(460001L);
        repository.claim(460001L, now, 1).orElseThrow();
        assertThat(repository.recover(now.plusSeconds(1), now.plusSeconds(10), 1)).isEqualTo(1);
        assertThat(repository.claim(460001L, now.plusSeconds(10), 1)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM media_search_document WHERE media_id = 460001", String.class))
            .isEqualTo("FAILED");
    }

    @Test
    void 미디어_삭제는_문서를_삭제하고_늦은_분석_저장을_막는다() {
        repository.register(460001L);
        var attempt = repository.claim(460001L, now, 3).orElseThrow();
        jdbc.update("DELETE FROM stored_file WHERE id = 460001");
        assertThat(repository.complete(attempt, analysis(), now)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_search_document WHERE media_id = 460001", Integer.class))
            .isZero();
    }

    @Test
    void 영벡터와_비정상_수치는_저장_전에_거절한다() {
        assertThatThrownBy(() -> analysis(List.of(0.0, 0.0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analysis(List.of(Double.NaN)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ImageAnalysis analysis() {
        return analysis(List.of(1.0, 0.0, 0.0));
    }

    private ImageAnalysis analysis(List<Double> vector) {
        return new ImageAnalysis("해변 단체 사진", "바다, 사람", "해변 단체 사진 바다 사람",
            vector, "test-embedding", "test-vision", "v1");
    }

    private void insert(long id, String type, String status) {
        jdbc.update("""
            INSERT INTO stored_file (id, room_id, uploader_id, original_file_name,
                media_type, file_size_bytes, storage_key, status, created_at, updated_at, reserved_at)
            VALUES (?, 1, 1, 'photo.jpg', ?, 100, ?, ?, CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, id, type, "test/" + id, status);
    }
}
