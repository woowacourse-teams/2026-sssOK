package com.sssok.infrastructure.persistence.folder;

import com.sssok.application.port.out.FolderMediaRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderMediaRepositoryAdapter implements FolderMediaRepository {

    private final FolderMediaJpaRepository jpaRepository;

    @Override
    public int attachToFolder(Long folderId, List<Long> mediaIds) {
        int updatedCount = 0;
        for (Long mediaId : mediaIds) {
            updatedCount += jpaRepository.insertIfAbsent(folderId, mediaId);
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
    public Map<Long, Long> countByFolderIds(List<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.countGroupByFolderIdIn(folderIds).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @Override
    public List<Long> findMediaIdsBelongingToAnyFolder(List<Long> mediaIds) {
        return jpaRepository.findDistinctMediaIdsByMediaIdIn(mediaIds);
    }

    @Override
    public Map<Long, List<Long>> findFolderIdsByMedia(List<Long> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findMediaFolderPairs(mediaIds).stream()
            .collect(Collectors.groupingBy(
                pair -> (Long) pair[0],
                Collectors.mapping(pair -> (Long) pair[1], Collectors.toList())));
    }

    @Override
    public List<Long> findFolderIdsContainingMedia(List<Long> mediaIds) {
        return jpaRepository.findDistinctFolderIdsByMediaIdIn(mediaIds);
    }
}
