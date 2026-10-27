package com.sssok.renewal.outgoing.database;

import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import com.sssok.infrastructure.persistence.folder.FolderJpaEntity;
import com.sssok.infrastructure.persistence.folder.FolderJpaRepository;
import com.sssok.infrastructure.persistence.folder.FolderMediaJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UploadFolderRepository {
    private final FolderJpaRepository folderJpaRepository;
    private final FolderMediaJpaRepository folderMediaJpaRepository;

    public List<Folder> findAllById(List<Long> folderIds) {
        return folderJpaRepository.findAllById(folderIds).stream()
            .map(this::toDomain).toList();
    }

    public Map<Long, List<Long>> findFolderIdsByMedia(List<Long> mediaIds) {
        return folderMediaJpaRepository.findMediaFolderPairs(mediaIds).stream()
            .collect(Collectors.groupingBy(
                pair -> (Long) pair[0],
                Collectors.mapping(pair -> (Long) pair[1], Collectors.toList())));
    }

    public void addFileToFolders(Long fileId, List<Long> folderIds) {
        for (Long folderId : folderIds) {
            folderMediaJpaRepository.insertIfAbsent(folderId, fileId);
        }
    }

    private Folder toDomain(FolderJpaEntity entity) {
        return Folder.reconstruct(
            entity.getId(), entity.getRoomId(), new FolderName(entity.getName()), entity.getCreatedAt());
    }
}
