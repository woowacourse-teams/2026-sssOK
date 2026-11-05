package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizeStartGateJpaRepository extends JpaRepository<OrganizeStartGateJpaEntity, Long> {

    // 마지막 접수가 threshold 이전일 때만 갱신된다. 동시에 두 요청이 와도 한쪽만 1건을 갱신한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeStartGateJpaEntity g set g.lastStartedAt = :now, g.updatedAt = :now
        where g.memberId = :memberId and g.lastStartedAt <= :threshold
        """)
    int touchIfIdle(@Param("memberId") Long memberId, @Param("now") Instant now,
                    @Param("threshold") Instant threshold);
}
