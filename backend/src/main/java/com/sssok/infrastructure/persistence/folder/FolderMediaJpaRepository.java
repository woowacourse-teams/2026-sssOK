package com.sssok.infrastructure.persistence.folder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderMediaJpaRepository extends JpaRepository<FolderMediaJpaEntity, Long> {

    long deleteByFolderId(Long folderId);

    long countByFolderId(Long folderId);

    // 동시에 같은 조합을 담으려는 요청이 있어도 관계는 하나만 남도록 DB에 맡긴다.
    @Modifying
    @Query(value = """
        INSERT INTO folder_media (folder_id, media_id)
        VALUES (:folderId, :mediaId)
        ON CONFLICT (folder_id, media_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("folderId") Long folderId, @Param("mediaId") Long mediaId);
}
