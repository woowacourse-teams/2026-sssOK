package com.sssok.infrastructure.persistence.folder;

import com.sssok.application.port.out.FolderMediaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderMediaRepositoryAdapter implements FolderMediaRepository {

    private final FolderMediaJpaRepository jpaRepository;

    @Override
    public int attachAll(List<Long> folderIds, List<Long> mediaIds) {
        int updatedCount = 0;
        for (Long folderId : folderIds) {
            for (Long mediaId : mediaIds) {
                updatedCount += jpaRepository.insertIfAbsent(folderId, mediaId);
            }
        }
        return updatedCount;
    }

    @Override
    public long detachAllFromFolder(Long folderId) {
        return jpaRepository.deleteByFolderId(folderId);
    }

    @Override
    public long countByFolderId(Long folderId) {
        return jpaRepository.countByFolderId(folderId);
    }
}
