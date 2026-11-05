package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DownloadJobJpaRepository extends JpaRepository<DownloadJobJpaEntity, Long> {

    long countByRequesterIdAndStatusIn(Long requesterId, List<String> statuses);

    List<DownloadJobJpaEntity> findAllByStatusAndReadyAtBefore(String status, Instant threshold);

    // 진행률을 남기면 updated_at 이 갱신되므로, 살아 있는 압축은 조건에서 빠진다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE download_job
        SET status = :failed, failure_reason = :reason, updated_at = now()
        WHERE status IN (:activeStatuses) AND updated_at < :threshold
        """, nativeQuery = true)
    int failStaleActive(
        @Param("activeStatuses") List<String> activeStatuses,
        @Param("failed") String failed,
        @Param("reason") String reason,
        @Param("threshold") Instant threshold
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE download_job
        SET requester_id = :targetMemberId, updated_at = now()
        WHERE requester_id = :sourceMemberId
        """, nativeQuery = true)
    int transferRequester(
        @Param("sourceMemberId") Long sourceMemberId,
        @Param("targetMemberId") Long targetMemberId
    );
}
