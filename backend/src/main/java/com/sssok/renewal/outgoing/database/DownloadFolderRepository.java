package com.sssok.renewal.outgoing.database;

import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import com.sssok.infrastructure.persistence.folder.FolderJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderJpaRepository;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DownloadFolderRepository {
    private final FolderJpaRepository folderJpaRepository;
    private final FolderMediaJpaRepository folderMediaJpaRepository;

    public Optional<Folder> findById(Long folderId) {
        return folderJpaRepository.findById(folderId).map(this::toDomain);
    }

    public List<Long> findMediaIdsByFolderId(Long folderId) {
        return folderMediaJpaRepository.findMediaIdsByFolderId(folderId);
    }

    private Folder toDomain(FolderJpaEntity entity) {
        return Folder.reconstruct(
            entity.getId(), entity.getRoomId(), new FolderName(entity.getName()), entity.getCreatedAt());
    }
}
