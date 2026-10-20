package com.sssok.renewal.application.media.upload.issue;

import com.sssok.domain.folder.Folder;
import com.sssok.renewal.domain.media.exception.UploadFolderNotFoundException;
import com.sssok.renewal.outgoing.database.UploadFolderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UploadFolderValidator {
    private final UploadFolderRepository uploadFolderRepository;

    public List<Long> validate(Long roomId, List<Long> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        List<Long> existingFolderIds = uploadFolderRepository.findAllById(folderIds).stream()
            .filter(folder -> folder.belongsTo(roomId))
            .map(Folder::getId).toList();
        List<Long> missingFolderIds = folderIds.stream().filter(id -> !existingFolderIds.contains(id)).toList();
        if (!missingFolderIds.isEmpty()) {
            throw new UploadFolderNotFoundException(missingFolderIds);
        }
        return existingFolderIds;
    }
}
