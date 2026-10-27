package com.sssok.infrastructure.persistence.download;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DownloadJobJpaRepository extends JpaRepository<DownloadJobJpaEntity, Long> {

    long countByRequesterIdAndStatusIn(Long requesterId, List<String> statuses);

    List<DownloadJobJpaEntity> findAllByStatusAndReadyAtBefore(String status, Instant threshold);

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
