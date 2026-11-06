package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizeRoomStateJpaRepository extends JpaRepository<OrganizeRoomStateJpaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update OrganizeRoomStateJpaEntity s set s.version = s.version + 1, s.busy = :busy, s.updatedAt = :now
        where s.roomId = :roomId
        """)
    int bump(@Param("roomId") Long roomId, @Param("busy") boolean busy, @Param("now") Instant now);
}
