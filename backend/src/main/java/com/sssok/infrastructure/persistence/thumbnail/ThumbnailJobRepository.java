package com.sssok.infrastructure.persistence.thumbnail;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThumbnailJobRepository extends JpaRepository<ThumbnailJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ThumbnailJob j where j.mediaId = :id")
    Optional<ThumbnailJob> lock(@Param("id") Long id);

    // 행 잠금은 선점 트랜잭션 동안만 유지한다. 다른 인스턴스가 고른 행은 기다리지 않는다.
    @Query(value = """
        SELECT * FROM thumbnail_job
        WHERE kind = :kind AND state IN ('PENDING', 'RETRY_WAIT') AND available_at <= :now
        ORDER BY available_at, media_id LIMIT 1 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<ThumbnailJob> claimable(@Param("kind") String kind, @Param("now") Instant now);

    @Query(value = """
        SELECT media_id FROM thumbnail_job
        WHERE state = 'RUNNING' AND lease_until <= :now
        ORDER BY lease_until, media_id LIMIT :limit
        """, nativeQuery = true)
    List<Long> expired(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
        SELECT sf.id FROM stored_file sf
        WHERE sf.status = 'PROCESSING'
          AND NOT EXISTS (SELECT 1 FROM thumbnail_job j WHERE j.media_id = sf.id)
        ORDER BY sf.id LIMIT :limit
        """, nativeQuery = true)
    List<Long> missing(@Param("limit") int limit);

    @Query("select j.state, count(j) from ThumbnailJob j group by j.state")
    List<Object[]> counts();

    @Query("select min(j.availableAt) from ThumbnailJob j where j.state in ('PENDING', 'RETRY_WAIT')")
    Instant oldestWaiting();
}
