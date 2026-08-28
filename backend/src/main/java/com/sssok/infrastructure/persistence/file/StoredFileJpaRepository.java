package com.sssok.infrastructure.persistence.file;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileJpaEntity, Long> {

    List<StoredFileJpaEntity> findAllByRoomId(Long roomId);

    List<StoredFileJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<String> statuses);

    List<StoredFileJpaEntity> findAllByRoomIdAndIdInAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<Long> ids, Collection<String> statuses);

    // 오래된 것부터 가져와, 밀린 작업이 뒤에서 계속 굶지 않게 한다.
    @Query("""
        select f.id from StoredFileJpaEntity f
        where f.status = :status and f.createdAt < :stuckBefore
        order by f.createdAt asc
        """)
    List<Long> findStuckInProcessing(@Param("status") String status,
                                     @Param("stuckBefore") Instant stuckBefore,
                                     Limit limit);

    void deleteAllByRoomId(Long roomId);
}
