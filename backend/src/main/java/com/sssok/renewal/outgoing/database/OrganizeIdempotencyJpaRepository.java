package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizeIdempotencyJpaRepository extends JpaRepository<OrganizeIdempotencyJpaEntity, Long> {

    Optional<OrganizeIdempotencyJpaEntity> findByMemberIdAndRoomIdAndOperationAndIdemKey(
        Long memberId, Long roomId, String operation, String idemKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OrganizeIdempotencyJpaEntity i where i.createdAt < :threshold")
    int deleteCreatedBefore(@Param("threshold") Instant threshold);
}
