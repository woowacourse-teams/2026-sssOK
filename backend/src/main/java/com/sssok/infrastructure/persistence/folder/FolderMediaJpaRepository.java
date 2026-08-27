package com.sssok.infrastructure.persistence.folder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderMediaJpaRepository extends JpaRepository<FolderMediaJpaEntity, Long> {

    long deleteByFolderId(Long folderId);

    long deleteByMediaIdIn(List<Long> mediaIds);

    long countByFolderId(Long folderId);

    @Query("select f.folderId, count(f) from FolderMediaJpaEntity f where f.folderId in :folderIds group by f.folderId")
    List<Object[]> countGroupByFolderIdIn(@Param("folderIds") List<Long> folderIds);

    @Modifying
    @Query("delete from FolderMediaJpaEntity f where f.folderId = :folderId and f.mediaId = :mediaId")
    int deleteIfPresent(@Param("folderId") Long folderId, @Param("mediaId") Long mediaId);

    @Query("select distinct f.mediaId from FolderMediaJpaEntity f where f.mediaId in :mediaIds")
    List<Long> findDistinctMediaIdsByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    // 미디어별로 어떤 폴더에 담겼는지. 미디어마다 따로 조회하면 N+1 이 된다.
    @Query("select f.mediaId, f.folderId from FolderMediaJpaEntity f where f.mediaId in :mediaIds")
    List<Object[]> findMediaFolderPairs(@Param("mediaIds") List<Long> mediaIds);

    @Query("select distinct f.folderId from FolderMediaJpaEntity f where f.mediaId in :mediaIds")
    List<Long> findDistinctFolderIdsByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    @Query("select f.mediaId from FolderMediaJpaEntity f where f.folderId = :folderId")
    List<Long> findMediaIdsByFolderId(@Param("folderId") Long folderId);

    // folder_media는 folder와 매핑 관계가 없는 순수 인프라 테이블이라(도메인 계층 없음),
    // room_id로 조인해 지우려면 네이티브 쿼리가 필요하다.
    @Modifying
    @Query(value = "DELETE FROM folder_media WHERE folder_id IN (SELECT id FROM folder WHERE room_id = :roomId)",
        nativeQuery = true)
    int deleteByRoomId(@Param("roomId") Long roomId);

    // 동시에 같은 조합을 담으려는 요청이 있어도 관계는 하나만 남도록 DB에 맡긴다.
    // JPA 를 거치지 않아 BaseEntity 의 @PrePersist 가 돌지 않으므로 감사 컬럼을 직접 넣는다.
    @Modifying
    @Query(value = """
        INSERT INTO folder_media (folder_id, media_id, created_at, updated_at)
        VALUES (:folderId, :mediaId, now(), now())
        ON CONFLICT (folder_id, media_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("folderId") Long folderId, @Param("mediaId") Long mediaId);
}
