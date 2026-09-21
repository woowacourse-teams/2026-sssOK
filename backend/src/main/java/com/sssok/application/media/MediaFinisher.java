package com.sssok.application.media;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 워커 결과를 DB 에 반영하는 부분만 짧게 트랜잭션으로 감싼다.
// GenerateThumbnailService 안에서 직접 호출하면 프록시를 타지 않아 @Transactional 이 걸리지 않으므로
// 별도 빈으로 둔다.
//
// 워커가 도는 사이 사용자가 같은 미디어를 지울 수 있다. 워커가 들고 있던 옛 엔티티를 그대로
// save() 하면 이미 없는 행에 merge 가 걸려 터지므로, 잠금 조회로 다시 읽어 살아 있는 행에만 쓴다.
@Component
@RequiredArgsConstructor
public class MediaFinisher {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void finish(Long mediaId, ProcessedMedia processed) {
        StoredFile file = lockProcessing(mediaId);
        if (file == null) {
            // 행이 없으면 ThumbnailSweeper 도 못 찾으므로, 방금 올린 썸네일은 여기서 지운다.
            discard(processed.thumbnailKey());
            return;
        }
        file.completeProcessing(processed);
        fileRepository.save(file);
        eventPublisher.publishEvent(new MediaReadyEvent(file.getRoomId(), file.getId()));
    }

    @Transactional
    public void markFailed(Long mediaId) {
        StoredFile file = lockProcessing(mediaId);
        if (file == null) {
            return;
        }
        file.failUpload();
        fileRepository.save(file);
    }

    private StoredFile lockProcessing(Long mediaId) {
        return fileRepository.findByIdForUpdate(mediaId)
            .filter(file -> file.getStatus() == UploadStatus.PROCESSING)
            .orElse(null);
    }

    private void discard(StorageKey thumbnailKey) {
        if (thumbnailKey == null) {
            return;
        }
        fileStoragePort.delete(thumbnailKey);
    }
}
