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

    @Modifying
    @Query("delete from FolderMediaJpaEntity f where f.folderId = :folderId and f.mediaId = :mediaId")
    int deleteIfPresent(@Param("folderId") Long folderId, @Param("mediaId") Long mediaId);

    @Query("select distinct f.mediaId from FolderMediaJpaEntity f where f.mediaId in :mediaIds")
    List<Long> findDistinctMediaIdsByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    @Query("select distinct f.folderId from FolderMediaJpaEntity f where f.mediaId in :mediaIds")
    List<Long> findDistinctFolderIdsByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    // folder_media는 folder와 매핑 관계가 없는 순수 인프라 테이블이라(도메인 계층 없음),
    // room_id로 조인해 지우려면 네이티브 쿼리가 필요하다.
    @Modifying
    @Query(value = "DELETE FROM folder_media WHERE folder_id IN (SELECT id FROM folder WHERE room_id = :roomId)",
        nativeQuery = true)
    int deleteByRoomId(@Param("roomId") Long roomId);

    // 동시에 같은 조합을 담으려는 요청이 있어도 관계는 하나만 남도록 DB에 맡긴다.
    @Modifying
    @Query(value = """
        INSERT INTO folder_media (folder_id, media_id)
        VALUES (:folderId, :mediaId)
        ON CONFLICT (folder_id, media_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("folderId") Long folderId, @Param("mediaId") Long mediaId);
}
