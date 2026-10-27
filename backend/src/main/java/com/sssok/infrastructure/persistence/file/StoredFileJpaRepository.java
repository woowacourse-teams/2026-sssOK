package com.sssok.infrastructure.persistence.file;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileJpaRepository extends JpaRepository<StoredFileJpaEntity, Long> {

    // 행을 지우는 트랜잭션이 잡은 배타 락을 기다렸다가 최신 상태를 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StoredFileJpaEntity> findWithLockById(Long id);

    List<StoredFileJpaEntity> findAllByRoomId(Long roomId);

    List<StoredFileJpaEntity> findAllByRoomIdAndIdIn(Long roomId, Collection<Long> ids);

    List<StoredFileJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<String> statuses);

    List<StoredFileJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<String> statuses, Limit limit);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        WHERE sf.room_id = :roomId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
        ORDER BY sf.created_at DESC, sf.id DESC
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findAllByRoomIdAndUploaderOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        WHERE sf.room_id = :roomId
          AND sf.status IN (:statuses)
          AND (sf.created_at, sf.id) < (:lastCreatedAt, :lastMediaId)
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findNextPageByRoomIdAndStatusInOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("statuses") Collection<String> statuses,
        @Param("lastCreatedAt") Instant lastCreatedAt,
        @Param("lastMediaId") Long lastMediaId,
        @Param("limit") int limit);

    long countByRoomIdAndStatusIn(Long roomId, Collection<String> statuses);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        WHERE sf.room_id = :roomId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findFirstPageByRoomIdAndUploaderOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader,
        @Param("limit") int limit);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        WHERE sf.room_id = :roomId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
          AND (sf.created_at, sf.id) < (:lastCreatedAt, :lastMediaId)
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findNextPageByRoomIdAndUploaderOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader,
        @Param("lastCreatedAt") Instant lastCreatedAt,
        @Param("lastMediaId") Long lastMediaId,
        @Param("limit") int limit);

    @Query(value = """
        SELECT COUNT(*)
        FROM stored_file sf
        WHERE sf.room_id = :roomId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
        """, nativeQuery = true)
    long countByRoomIdAndUploader(
        @Param("roomId") Long roomId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader);

    List<StoredFileJpaEntity> findAllByRoomIdAndIdInAndStatusInOrderByCreatedAtDescIdDesc(
        Long roomId, Collection<Long> ids, Collection<String> statuses);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        JOIN folder_media fm ON fm.media_id = sf.id
        WHERE sf.room_id = :roomId
          AND fm.folder_id = :folderId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
        ORDER BY sf.created_at DESC, sf.id DESC
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findAllByRoomIdAndFolderIdAndUploaderOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader);

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

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        JOIN folder_media fm ON fm.media_id = sf.id
        WHERE sf.room_id = :roomId
          AND fm.folder_id = :folderId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findFirstPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader,
        @Param("limit") int limit);

    @Query(value = """
        SELECT sf.*
        FROM stored_file sf
        JOIN folder_media fm ON fm.media_id = sf.id
        WHERE sf.room_id = :roomId
          AND fm.folder_id = :folderId
          AND sf.status IN (:statuses)
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
          AND (sf.created_at, sf.id) < (:lastCreatedAt, :lastMediaId)
        ORDER BY sf.created_at DESC, sf.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<StoredFileJpaEntity> findNextPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader,
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
          AND (:uploader = 'ALL'
            OR (:uploader = 'ME' AND sf.uploader_id = :requesterId)
            OR (:uploader = 'OTHERS' AND sf.uploader_id <> :requesterId))
        """, nativeQuery = true)
    long countByRoomIdAndFolderIdAndUploader(
        @Param("roomId") Long roomId,
        @Param("folderId") Long folderId,
        @Param("statuses") Collection<String> statuses,
        @Param("requesterId") Long requesterId,
        @Param("uploader") String uploader);

    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE stored_file
        SET uploader_id = :targetMemberId, updated_at = now()
        WHERE uploader_id = :sourceMemberId
        """, nativeQuery = true)
    int transferUploader(
        @Param("sourceMemberId") Long sourceMemberId,
        @Param("targetMemberId") Long targetMemberId
    );
}
