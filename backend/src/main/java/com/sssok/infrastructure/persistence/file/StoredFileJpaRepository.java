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

    List<StoredFileJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<String> statuses, Limit limit);

    @Query("""
        select f from StoredFileJpaEntity f
        where f.roomId = :roomId
          and f.status in :statuses
          and (f.createdAt < :lastCreatedAt
            or (f.createdAt = :lastCreatedAt and f.id < :lastMediaId))
        order by f.createdAt desc, f.id desc
        """)
    List<StoredFileJpaEntity> findNextPageByRoomIdAndStatusInOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("statuses") Collection<String> statuses,
        @Param("lastCreatedAt") Instant lastCreatedAt,
        @Param("lastMediaId") Long lastMediaId,
        Limit limit);

    long countByRoomIdAndStatusIn(Long roomId, Collection<String> statuses);

    List<StoredFileJpaEntity> findAllByRoomIdAndIdInAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<Long> ids, Collection<String> statuses);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        JOIN folder_media fm ON fm.media_id = sf.id
        WHERE sf.room_id = :roomId
          AND fm.folder_id = :folderId
          AND sf.status IN (:statuses)
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findFirstPageByRoomIdAndFolderIdAndStatusInOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses,
        @Param("limit") int limit);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        JOIN folder_media fm ON fm.media_id = sf.id
        WHERE sf.room_id = :roomId
          AND fm.folder_id = :folderId
          AND sf.status IN (:statuses)
          AND (sf.created_at, sf.id) < (:lastCreatedAt, :lastMediaId)
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findNextPageByRoomIdAndFolderIdAndStatusInOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses,
        @Param("lastCreatedAt") Instant lastCreatedAt,
        @Param("lastMediaId") Long lastMediaId,
        @Param("limit") int limit);

    @Query(value = """
        SELECT COUNT(*)
        FROM stored_file sf
        JOIN folder_media fm ON fm.media_id = sf.id
        WHERE sf.room_id = :roomId
          AND fm.folder_id = :folderId
          AND sf.status IN (:statuses)
        """, nativeQuery = true)
    long countByRoomIdAndFolderIdAndStatusIn(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses);

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
