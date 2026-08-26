package com.sssok.infrastructure.persistence.file;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileJpaEntity, Long> {

    List<StoredFileJpaEntity> findAllByRoomId(Long roomId);

    void deleteAllByRoomId(Long roomId);
}
