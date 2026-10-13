package com.sssok.infrastructure.persistence.room;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomJpaRepository extends JpaRepository<RoomJpaEntity, Long> {

    Optional<RoomJpaEntity> findByCode(String code);
}
