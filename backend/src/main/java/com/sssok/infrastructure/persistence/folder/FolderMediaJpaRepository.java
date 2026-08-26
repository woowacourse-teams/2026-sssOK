package com.sssok.infrastructure.persistence.folder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderMediaJpaRepository extends JpaRepository<FolderMediaJpaEntity, Long> {

    long deleteByFolderId(Long folderId);

    // folder_media는 folder와 매핑 관계가 없는 순수 인프라 테이블이라(도메인 계층 없음),
    // room_id로 조인해 지우려면 네이티브 쿼리가 필요하다.
    @Modifying
    @Query(value = "DELETE FROM folder_media WHERE folder_id IN (SELECT id FROM folder WHERE room_id = :roomId)",
        nativeQuery = true)
    int deleteByRoomId(@Param("roomId") Long roomId);
}
