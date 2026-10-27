package com.sssok.renewal.application.media.upload.complete;

import com.sssok.application.media.MediaCreatedEvent;
import com.sssok.application.media.MediaDetail;
import com.sssok.application.media.MediaUrlResolver;
import com.sssok.domain.file.StoredFile;
import com.sssok.renewal.application.media.thumbnail.ThumbnailJobService;
import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.database.UploadFolderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaRegistrationService {

    private final UploadFileRepository uploadFileRepository;
    private final ThumbnailJobService thumbnailJobService;
    private final UploadFolderRepository uploadFolderRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final ApplicationEventPublisher eventPublisher;

    // 스토리지 검증은 호출 전에 끝낸다. 파일 상태와 썸네일 작업은 함께 커밋하거나 롤백한다.
    @Transactional
    public List<MediaDetail> register(Long roomId, List<StoredFile> verifiedFiles, String uploaderName) {
        if (verifiedFiles.isEmpty()) {
            return List.of();
        }
        List<Long> mediaIds = verifiedFiles.stream().map(StoredFile::getId).toList();
        Map<Long, List<Long>> folderIdsByMediaId = uploadFolderRepository.findFolderIdsByMedia(mediaIds);

        List<MediaDetail> registeredMedia = new ArrayList<>();
        for (StoredFile verifiedFile : verifiedFiles) {
            StoredFile processingFile = registerForProcessing(verifiedFile.getId());
            List<Long> mediaFolderIds = folderIdsByMediaId.getOrDefault(processingFile.getId(), List.of());
            registeredMedia.add(toMediaDetail(processingFile, uploaderName, mediaFolderIds));
        }
        publishMediaCreatedEvents(roomId, registeredMedia);
        return registeredMedia;
    }

    private StoredFile registerForProcessing(Long mediaId) {
        // 검증 이후 상태가 바뀔 수 있으므로 잠금을 잡고 최신 파일을 다시 읽는다.
        StoredFile lockedFile = uploadFileRepository.findByIdForUpdate(mediaId).orElseThrow();
        lockedFile.startProcessing();
        StoredFile processingFile = uploadFileRepository.save(lockedFile);
        thumbnailJobService.enqueue(processingFile);
        return processingFile;
    }

    private MediaDetail toMediaDetail(StoredFile processingFile, String uploaderName, List<Long> folderIds) {
        String originalNickname = processingFile.getUploaderNickname();
        return MediaDetail.of(processingFile, originalNickname == null ? uploaderName : originalNickname, folderIds,
            mediaUrlResolver.resolve(processingFile), 0, false);
    }

    private void publishMediaCreatedEvents(Long roomId, List<MediaDetail> registeredMedia) {
        // 이벤트는 현재 트랜잭션에 등록하고, 기존 AFTER_COMMIT 리스너가 커밋 후 SSE로 발행한다.
        for (MediaDetail media : registeredMedia) {
            eventPublisher.publishEvent(new MediaCreatedEvent(roomId, media));
        }
    }
}
