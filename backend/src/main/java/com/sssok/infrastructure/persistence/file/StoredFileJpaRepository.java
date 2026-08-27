package com.sssok.infrastructure.persistence.file;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileJpaEntity, Long> {

    List<StoredFileJpaEntity> findAllByRoomId(Long roomId);

    List<StoredFileJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<String> statuses);

    List<StoredFileJpaEntity> findAllByRoomIdAndIdInAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<Long> ids, Collection<String> statuses);


    void deleteAllByRoomId(Long roomId);
}
