package com.sssok.application.media;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.FolderMediaRepository;
import com.sssok.domain.file.StoredFile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MediaDeleter {

    private final FileRepository fileRepository;
    private final FolderMediaRepository folderMediaRepository;
    private final FileStoragePort fileStoragePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void delete(Long roomId, List<StoredFile> files) {
        for (StoredFile file : files) {
            fileStoragePort.delete(file.getStorageKey());
            if (file.getThumbnailKey() != null) {
                fileStoragePort.delete(file.getThumbnailKey());
            }
        }

        List<Long> mediaIds = files.stream().map(StoredFile::getId).toList();
        folderMediaRepository.detachFromAllFolders(mediaIds);
        fileRepository.deleteAllByIdIn(mediaIds);
        eventPublisher.publishEvent(new MediaDeletedEvent(roomId, mediaIds));
    }
}
