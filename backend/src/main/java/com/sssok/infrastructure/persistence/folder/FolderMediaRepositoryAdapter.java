package com.sssok.infrastructure.persistence.folder;

import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.UploadStatus;
import java.util.Collection;
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
    public long countByFolderIdAndStatusIn(Long folderId, Collection<UploadStatus> statuses) {
        if (statuses.isEmpty()) {
            return 0L;
        }
        return jpaRepository.countByFolderIdAndStatusIn(folderId, names(statuses));
    }

    @Override
    public Map<Long, Long> countByFolderIdsAndStatusIn(List<Long> folderIds, Collection<UploadStatus> statuses) {
        if (folderIds.isEmpty() || statuses.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.countGroupByFolderIdInAndStatusIn(folderIds, names(statuses)).stream()
            .collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> ((Number) row[1]).longValue()));
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

    @Override
    public List<Long> findMediaIdsByFolderId(Long folderId) {
        return jpaRepository.findMediaIdsByFolderId(folderId);
    }

    private List<String> names(Collection<UploadStatus> statuses) {
        return statuses.stream().map(UploadStatus::name).toList();
    }
}
