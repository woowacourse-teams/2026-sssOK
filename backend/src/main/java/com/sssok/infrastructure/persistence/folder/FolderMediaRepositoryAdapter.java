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
    public int detachFromFolders(List<Long> folderIds, List<Long> mediaIds) {
        int removedCount = 0;
        for (Long folderId : folderIds) {
            for (Long mediaId : mediaIds) {
                removedCount += jpaRepository.deleteIfPresent(folderId, mediaId);
            }
        }
        return removedCount;
    }

    @Override
    public long detachFromAllFolders(List<Long> mediaIds) {
        return jpaRepository.deleteByMediaIdIn(mediaIds);
    }

    @Override
    public long detachAllByRoomId(Long roomId) {
        return jpaRepository.deleteByRoomId(roomId);
    }

    @Override
    public long countByFolderId(Long folderId) {
        return jpaRepository.countByFolderId(folderId);
    }

    @Override
    public List<Long> findMediaIdsBelongingToAnyFolder(List<Long> mediaIds) {
        return jpaRepository.findDistinctMediaIdsByMediaIdIn(mediaIds);
    }

    @Override
    public List<Long> findFolderIdsContainingMedia(List<Long> mediaIds) {
        return jpaRepository.findDistinctFolderIdsByMediaIdIn(mediaIds);
    }
}
