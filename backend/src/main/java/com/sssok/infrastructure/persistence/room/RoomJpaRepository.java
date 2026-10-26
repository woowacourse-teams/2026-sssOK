package com.sssok.infrastructure.persistence.room;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomJpaRepository extends JpaRepository<RoomJpaEntity, Long> {

    Optional<RoomJpaEntity> findByCode(String code);

    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE room
        SET host_id = :targetMemberId, version = version + 1, updated_at = now()
        WHERE host_id = :sourceMemberId
        """, nativeQuery = true)
    int transferHost(
        @Param("sourceMemberId") Long sourceMemberId,
        @Param("targetMemberId") Long targetMemberId
    );
}
