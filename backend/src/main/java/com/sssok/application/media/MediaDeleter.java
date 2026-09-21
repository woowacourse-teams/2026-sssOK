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

    // 행을 먼저 지우고 스토리지를 정리한다. 순서를 뒤집으면 스토리지를 치운 뒤 행이 지워지기
    // 전에 워커가 썸네일을 올려 고아 객체가 남는다.
    @Transactional
    public void delete(Long roomId, List<StoredFile> files) {
        List<Long> mediaIds = files.stream().map(StoredFile::getId).toList();
        folderMediaRepository.detachFromAllFolders(mediaIds);
        fileRepository.deleteAllByIdIn(mediaIds);

        for (StoredFile file : files) {
            fileStoragePort.delete(file.getStorageKey());
            // thumbnailKey 는 워커가 방금 채웠어도 이 트랜잭션엔 null 로 보인다. 원본에서 유도해 지운다.
            fileStoragePort.delete(file.getStorageKey().thumbnail());
        }

        eventPublisher.publishEvent(new MediaDeletedEvent(roomId, mediaIds));
    }
}
