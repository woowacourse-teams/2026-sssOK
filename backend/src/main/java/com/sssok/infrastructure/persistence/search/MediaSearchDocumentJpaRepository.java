package com.sssok.infrastructure.persistence.search;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaSearchDocumentJpaRepository extends JpaRepository<MediaSearchDocumentJpaEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO media_search_document (media_id)
        SELECT f.id FROM stored_file f
        WHERE f.media_type IN ('JPEG', 'PNG', 'GIF') AND f.status = 'READY' AND f.created_at >= :createdSince
            AND NOT EXISTS (SELECT 1 FROM media_search_document d WHERE d.media_id = f.id)
        ORDER BY f.id LIMIT :limit FOR KEY SHARE OF f
        ON CONFLICT (media_id) DO NOTHING
        """, nativeQuery = true)
    int registerMissing(@Param("createdSince") Instant createdSince, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO media_search_document (media_id)
            SELECT id FROM stored_file
            WHERE id = :mediaId AND media_type IN ('JPEG', 'PNG', 'GIF') AND status = 'READY'
            FOR KEY SHARE
            ON CONFLICT (media_id) DO NOTHING
        """, nativeQuery = true)
    int register(@Param("mediaId") Long mediaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE media_search_document d
            SET status = 'PROCESSING', attempts = attempts + 1,
                started_at = :now, updated_at = :now, error_code = NULL
            WHERE media_id = :mediaId AND status = 'PENDING'
                AND next_attempt_at <= :now AND attempts < :maxAttempts
                AND EXISTS (SELECT 1 FROM stored_file f
                    WHERE f.id = d.media_id AND f.status = 'READY' AND f.media_type IN ('JPEG', 'PNG', 'GIF'))
        """, nativeQuery = true)
    int claim(
        @Param("now") Instant now,
        @Param("mediaId") Long mediaId,
        @Param("maxAttempts") int maxAttempts);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE media_search_document d SET status = 'READY', description = :description, features = :features,
                search_text = :searchText, embedding = CAST(:vector AS vector),
                embedding_model = :embeddingModel, embedding_dimensions = :dimensions,
                analysis_model = :analysisModel, prompt_version = :promptVersion, completed_at = :now, updated_at = :now, error_code = NULL
            WHERE media_id = :mediaId AND status = 'PROCESSING' AND attempts = :attemptNumber
                AND EXISTS (SELECT 1 FROM stored_file f
                    WHERE f.id = d.media_id AND f.status = 'READY' AND f.media_type IN ('JPEG', 'PNG', 'GIF'))
        """, nativeQuery = true)
    int complete(
        @Param("description") String description,
        @Param("features") String features,
        @Param("searchText") String searchText,
        @Param("vector") String vector,
        @Param("embeddingModel") String embeddingModel,
        @Param("dimensions") int dimensions,
        @Param("analysisModel") String analysisModel,
        @Param("promptVersion") String promptVersion,
        @Param("now") Instant now,
        @Param("mediaId") Long mediaId,
        @Param("attemptNumber") int attemptNumber);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE media_search_document
            SET status = CASE WHEN attempts >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END,
                error_code = :errorCode, next_attempt_at = :retryAt, updated_at = CURRENT_TIMESTAMP
            WHERE media_id = :mediaId AND status = 'PROCESSING' AND attempts = :attemptNumber
        """, nativeQuery = true)
    int fail(
        @Param("maxAttempts") int maxAttempts,
        @Param("errorCode") String errorCode,
        @Param("retryAt") Instant retryAt,
        @Param("mediaId") Long mediaId,
        @Param("attemptNumber") int attemptNumber);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE media_search_document
            SET status = CASE WHEN attempts >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END,
                error_code = 'WORKER_INTERRUPTED', next_attempt_at = :now, updated_at = :now
            WHERE status = 'PROCESSING' AND started_at < :stuckBefore
        """, nativeQuery = true)
    int recover(
        @Param("maxAttempts") int maxAttempts,
        @Param("now") Instant now,
        @Param("stuckBefore") Instant stuckBefore);

    @Query(value = """
            SELECT d.media_id FROM media_search_document d
            JOIN stored_file f ON f.id = d.media_id
            WHERE d.status = 'PENDING' AND d.next_attempt_at <= :now
                AND f.status = 'READY' AND f.media_type IN ('JPEG', 'PNG', 'GIF')
            ORDER BY d.next_attempt_at, d.media_id LIMIT :limit
        """, nativeQuery = true)
    List<Long> findPending(@Param("now") Instant now, @Param("limit") int limit);

    // MATERIALIZED로 모델·차원·방 선별을 먼저 완료하여 다른 차원의 거리 계산을 막는다.
    @Query(value = """
        WITH candidates AS MATERIALIZED (
            SELECT d.media_id, d.embedding
            FROM media_search_document d JOIN stored_file f ON f.id = d.media_id
            WHERE f.room_id = :roomId AND f.status = 'READY'
                AND f.media_type IN ('JPEG', 'PNG', 'GIF') AND d.status = 'READY'
                AND d.embedding_model = :model AND d.embedding_dimensions = :dimensions
                AND vector_dims(d.embedding) = :dimensions
        ), scored AS (
            SELECT media_id, 1 - (embedding <=> CAST(:vector AS vector)) AS similarity
            FROM candidates
        )
        SELECT media_id AS "mediaId", similarity FROM scored
        WHERE similarity >= :threshold
        ORDER BY similarity DESC, media_id ASC
        """, nativeQuery = true)
    List<SearchMatch> search(@Param("roomId") Long roomId, @Param("vector") String vector,
        @Param("model") String model, @Param("dimensions") int dimensions,
        @Param("threshold") double threshold);

    interface SearchMatch {
        Long getMediaId();
        double getSimilarity();
    }
}
