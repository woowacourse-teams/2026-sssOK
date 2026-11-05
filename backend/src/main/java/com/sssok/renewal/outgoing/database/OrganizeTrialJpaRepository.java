package com.sssok.renewal.outgoing.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizeTrialJpaRepository extends JpaRepository<OrganizeTrialJpaEntity, Long> {

    boolean existsByMemberIdAndRoomId(Long memberId, Long roomId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OrganizeTrialJpaEntity t where t.jobId = :jobId")
    int deleteByJobId(@Param("jobId") Long jobId);
}
