package com.sssok.infrastructure.persistence.file;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrphanObjectJpaRepository extends JpaRepository<OrphanObjectJpaEntity, Long> {

    // 한 번도 시도하지 않은 행을 먼저 준다. 비동기 정리가 통째로 유실된 경우가 가장 급하다.
    // 그 다음은 가장 오래 방치된 순서라, 계속 실패하는 키가 뒤로 밀리며 다른 키를 굶기지 않는다.
    @Query("""
        select o from OrphanObjectJpaEntity o
        where o.createdAt < :retryBefore
          and (o.lastAttemptedAt is null or o.lastAttemptedAt < :retryBefore)
        order by o.lastAttemptedAt asc nulls first, o.id asc
        """)
    List<OrphanObjectJpaEntity> findStale(@Param("retryBefore") Instant retryBefore, Limit limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OrphanObjectJpaEntity o
        set o.attempts = o.attempts + 1, o.lastAttemptedAt = :attemptedAt
        where o.id in :ids
        """)
    int markAttempted(@Param("ids") List<Long> ids, @Param("attemptedAt") Instant attemptedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OrphanObjectJpaEntity o where o.storageKey in :storageKeys")
    int deleteByStorageKeyIn(@Param("storageKeys") List<String> storageKeys);
}
