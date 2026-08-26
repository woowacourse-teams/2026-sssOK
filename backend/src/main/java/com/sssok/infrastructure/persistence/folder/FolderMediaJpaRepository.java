package com.sssok.infrastructure.persistence.folder;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderMediaJpaRepository extends JpaRepository<FolderMediaJpaEntity, Long> {

    long deleteByFolderId(Long folderId);
}
