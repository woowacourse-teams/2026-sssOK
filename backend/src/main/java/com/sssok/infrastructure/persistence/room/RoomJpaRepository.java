package com.sssok.infrastructure.persistence.room;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomJpaRepository extends JpaRepository<RoomJpaEntity, Long> {

    Optional<RoomJpaEntity> findByCode(String code);

    // 방장이 지운 방은 deleted_at, 그냥 만료된 방은 expires_at 이 끝난 시각이다.
    @Query("SELECT r FROM RoomJpaEntity r WHERE COALESCE(r.deletedAt, r.expiresAt) < :threshold")
    List<RoomJpaEntity> findAllPurgeTargets(@Param("threshold") Instant threshold);
}
