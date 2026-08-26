package com.sssok.infrastructure.persistence.folder;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderJpaRepository extends JpaRepository<FolderJpaEntity, Long> {

    Optional<FolderJpaEntity> findByRoomIdAndName(Long roomId, String name);

    List<FolderJpaEntity> findAllByRoomIdOrderByCreatedAtAsc(Long roomId);

    void deleteByRoomId(Long roomId);
}
