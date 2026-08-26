package com.sssok.infrastructure.persistence.folder;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderJpaRepository extends JpaRepository<FolderJpaEntity, Long> {

    Optional<FolderJpaEntity> findByRoomIdAndName(Long roomId, String name);
}
