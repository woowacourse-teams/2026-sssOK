package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 상태 변경은 모두 조건부 UPDATE 다. 반환값이 0 이면 다른 쪽이 먼저 상태를 바꾼 것이다.
// 상태나 진행이 바뀔 때마다 job_version 을 1씩 올린다.
public interface OrganizeJobJpaRepository extends JpaRepository<OrganizeJobJpaEntity, Long> {

    Optional<OrganizeJobJpaEntity> findByPublicId(UUID publicId);

    Optional<OrganizeJobJpaEntity> findByActiveRoomId(Long activeRoomId);

    Optional<OrganizeJobJpaEntity>
        findFirstByRoomIdAndRequesterIdAndStatusInAndFinishedAtAfterOrderByFinishedAtDescIdDesc(
        Long roomId, Long requesterId, Collection<String> statuses, Instant finishedAfter);

    @Query("select j.id from OrganizeJobJpaEntity j where j.status = 'READY' and j.reviewExpiresAt <= :now order by j.id")
    List<Long> findReviewOverdueIds(@Param("now") Instant now, Pageable pageable);

    // MCP 가 접수하면 RUNNING 으로 바꾸되 아직 분석이 시작된 것은 아니므로 단계는 waiting 이다.
    // QUEUED 일 때만 걸리므로, 분석 서버의 진행 이벤트가 먼저 도착했다면 늦은 접수 응답이 그 진행을 덮어쓰지 않는다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'RUNNING', j.progressStage = 'waiting', j.progressProcessed = null,
            j.progressTotal = null, j.progressUnit = null, j.jobVersion = j.jobVersion + 1, j.updatedAt = :now
        where j.id = :id and j.status = 'QUEUED'
        """)
    int markRunning(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'RUNNING', j.progressStage = :stage, j.progressProcessed = :processed,
            j.progressTotal = :total, j.progressUnit = :unit, j.jobVersion = j.jobVersion + 1, j.updatedAt = :now
        where j.id = :id and j.jobVersion = :expectedVersion and j.status in ('QUEUED', 'RUNNING')
        """)
    int updateProgress(@Param("id") Long id, @Param("expectedVersion") int expectedVersion,
                       @Param("stage") String stage, @Param("processed") Integer processed,
                       @Param("total") Integer total, @Param("unit") String unit, @Param("now") Instant now);

    // READY 는 검토 중이라 방을 계속 점유한다(active_room_id 유지). 검토 기한과 결과 보관 기한을 함께 정한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'READY', j.resultVersion = :version, j.result = :result,
            j.reviewExpiresAt = :reviewExpiresAt, j.resultExpiresAt = :resultExpiresAt,
            j.progressStage = null, j.progressProcessed = null, j.progressTotal = null, j.progressUnit = null,
            j.jobVersion = j.jobVersion + 1, j.updatedAt = :now
        where j.id = :id and j.status in ('QUEUED', 'RUNNING')
        """)
    int markReady(@Param("id") Long id, @Param("version") int version, @Param("result") String result,
                  @Param("reviewExpiresAt") Instant reviewExpiresAt, @Param("resultExpiresAt") Instant resultExpiresAt,
                  @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'FAILED', j.activeRoomId = null, j.failureCode = :code, j.failureDetail = :detail,
            j.jobVersion = j.jobVersion + 1, j.finishedAt = :now, j.updatedAt = :now
        where j.id = :id and j.status in ('QUEUED', 'RUNNING')
        """)
    int markFailed(@Param("id") Long id, @Param("code") String code, @Param("detail") String detail,
                   @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'CANCELED', j.activeRoomId = null, j.result = null, j.resultExpiresAt = null,
            j.jobVersion = j.jobVersion + 1, j.finishedAt = :now, j.updatedAt = :now
        where j.id = :id and j.status in ('QUEUED', 'RUNNING', 'READY')
        """)
    int markCanceled(@Param("id") Long id, @Param("now") Instant now);

    // 검토 기한이 지난 READY 만 만료시킨다. 결과 본문은 보관 기한까지 남기지만 사용자에게는 410 이다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'EXPIRED', j.activeRoomId = null, j.jobVersion = j.jobVersion + 1,
            j.finishedAt = :now, j.updatedAt = :now
        where j.id = :id and j.status = 'READY' and j.reviewExpiresAt <= :now
        """)
    int markExpired(@Param("id") Long id, @Param("now") Instant now);

    // 기한 안의 READY 만 반영할 수 있다. 결과 본문은 보관 기한까지 남겨 APPLIED 로도 조회할 수 있게 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'APPLIED', j.activeRoomId = null, j.jobVersion = j.jobVersion + 1,
            j.finishedAt = :now, j.updatedAt = :now
        where j.id = :id and j.status = 'READY' and j.reviewExpiresAt > :now
        """)
    int markApplied(@Param("id") Long id, @Param("now") Instant now);

    // FAILED → QUEUED. 방을 다시 점유하므로 다른 작업이 방을 쓰고 있으면 active_room_id 유니크가 거절한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j
        set j.status = 'QUEUED', j.activeRoomId = j.roomId, j.failureCode = null, j.failureDetail = null,
            j.progressStage = null, j.progressProcessed = null, j.progressTotal = null, j.progressUnit = null,
            j.retryCount = j.retryCount + :retryIncrement, j.finishedAt = null,
            j.jobVersion = j.jobVersion + 1, j.updatedAt = :now
        where j.id = :id and j.status = 'FAILED'
        """)
    int markRetried(@Param("id") Long id, @Param("retryIncrement") int retryIncrement, @Param("now") Instant now);

    // 보관 기한이 지나면 결과 본문만 비운다. 작업 행(상태·사유)은 남긴다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeJobJpaEntity j set j.result = null, j.updatedAt = :now
        where j.result is not null and j.resultExpiresAt <= :now
        """)
    int purgeExpiredResults(@Param("now") Instant now);
}
