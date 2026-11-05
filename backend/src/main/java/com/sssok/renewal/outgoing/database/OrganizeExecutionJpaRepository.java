package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizeExecutionJpaRepository extends JpaRepository<OrganizeExecutionJpaEntity, Long> {

    Optional<OrganizeExecutionJpaEntity> findByExecutionId(UUID executionId);

    Optional<OrganizeExecutionJpaEntity> findFirstByJobIdOrderByAttemptDesc(Long jobId);

    @Query("""
        select e.id from OrganizeExecutionJpaEntity e
        where (e.status in ('DISPATCH_PENDING', 'DISPATCH_UNKNOWN') and e.nextDispatchAt <= :now)
           or (e.status = 'DISPATCHING' and e.leaseUntil < :now)
        order by e.id
        """)
    List<Long> findDispatchableIds(@Param("now") Instant now, Pageable pageable);

    // 선점. 대기·불확실 행이거나 임대가 끝난 발송 중 행만 잡을 수 있어서 여러 스레드·서버가 동시에 와도 한 곳만 성공한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeExecutionJpaEntity e
        set e.status = 'DISPATCHING', e.leaseUntil = :lease, e.dispatchAttempts = e.dispatchAttempts + 1,
            e.updatedAt = :now
        where e.id = :id
          and ((e.status in ('DISPATCH_PENDING', 'DISPATCH_UNKNOWN') and e.nextDispatchAt <= :now)
            or (e.status = 'DISPATCHING' and e.leaseUntil < :now))
        """)
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("lease") Instant lease);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeExecutionJpaEntity e
        set e.status = 'ACCEPTED', e.acceptedAt = :now, e.leaseUntil = null, e.updatedAt = :now
        where e.id = :id and e.status in ('DISPATCH_PENDING', 'DISPATCHING', 'DISPATCH_UNKNOWN')
        """)
    int markAccepted(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeExecutionJpaEntity e
        set e.status = :status, e.nextDispatchAt = :next, e.leaseUntil = null, e.updatedAt = :now
        where e.id = :id and e.status = 'DISPATCHING'
        """)
    int scheduleRetry(@Param("id") Long id, @Param("status") String status, @Param("next") Instant next,
                      @Param("now") Instant now);

    // Python 이벤트는 항상 "받았음"의 증거라, 발송 재시도보다 먼저 도착해도 상태를 올린다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeExecutionJpaEntity e
        set e.status = 'RUNNING', e.lastEventAt = :now, e.leaseUntil = null,
            e.acceptedAt = coalesce(e.acceptedAt, :now), e.startedAt = coalesce(e.startedAt, :now),
            e.updatedAt = :now
        where e.id = :id and e.status in ('DISPATCH_PENDING', 'DISPATCHING', 'DISPATCH_UNKNOWN', 'ACCEPTED', 'RUNNING')
        """)
    int markRunning(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeExecutionJpaEntity e
        set e.status = :status, e.finishedAt = :now, e.leaseUntil = null, e.updatedAt = :now
        where e.id = :id
          and e.status in ('DISPATCH_PENDING', 'DISPATCHING', 'DISPATCH_UNKNOWN', 'ACCEPTED', 'RUNNING')
        """)
    int markFinished(@Param("id") Long id, @Param("status") String status, @Param("now") Instant now);

    // 전달만 실패해 닫힌 실행을 같은 실행 ID 로 다시 발송 대기로 돌린다. 새 분석을 만들지 않는다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeExecutionJpaEntity e
        set e.status = 'DISPATCH_PENDING', e.nextDispatchAt = :now, e.dispatchDeadlineAt = :deadline,
            e.dispatchAttempts = 0, e.leaseUntil = null, e.finishedAt = null, e.updatedAt = :now
        where e.id = :id and e.status = 'FAILED'
        """)
    int reschedule(@Param("id") Long id, @Param("now") Instant now, @Param("deadline") Instant deadline);

    @Query("""
        select e from OrganizeExecutionJpaEntity e
        where e.status in ('DISPATCH_PENDING', 'DISPATCH_UNKNOWN', 'DISPATCHING') and e.dispatchDeadlineAt < :now
        """)
    List<OrganizeExecutionJpaEntity> findDispatchExpired(@Param("now") Instant now);

    @Query("select e from OrganizeExecutionJpaEntity e where e.status = 'ACCEPTED' and e.acceptedAt < :threshold")
    List<OrganizeExecutionJpaEntity> findNotStarted(@Param("threshold") Instant threshold);

    @Query("select e from OrganizeExecutionJpaEntity e where e.status = 'RUNNING' and e.lastEventAt < :threshold")
    List<OrganizeExecutionJpaEntity> findLost(@Param("threshold") Instant threshold);

    @Query("select e from OrganizeExecutionJpaEntity e where e.status = 'RUNNING' and e.startedAt < :threshold")
    List<OrganizeExecutionJpaEntity> findRunTooLong(@Param("threshold") Instant threshold);
}
