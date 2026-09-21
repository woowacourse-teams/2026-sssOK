package com.sssok.application.media;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.storage.ObjectsOrphanedEvent;
import com.sssok.application.storage.OrphanObjectCollector;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 워커 결과를 DB 에 반영하는 부분만 짧게 트랜잭션으로 감싼다.
// GenerateThumbnailService 안에서 직접 호출하면 프록시를 타지 않아 @Transactional 이 걸리지 않으므로
// 별도 빈으로 둔다.
//
// 워커가 도는 사이 사용자가 같은 미디어를 지우거나, ThumbnailSweeper 가 같은 미디어에 워커를
// 하나 더 태울 수 있다. 그래서 잠금 조회로 행을 다시 읽어, 살아 있는 PROCESSING 행에만 쓴다.
@Component
@RequiredArgsConstructor
public class MediaFinisher {

    private final FileRepository fileRepository;
    private final OrphanObjectCollector orphanObjectCollector;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void finish(Long mediaId, ProcessedMedia processed) {
        StoredFile file = fileRepository.findByIdForUpdate(mediaId).orElse(null);
        if (file == null) {
            // 행이 없으면 ThumbnailSweeper 도 못 찾으므로, 방금 올린 썸네일을 회수 대기열에 남긴다.
            // 실제 스토리지 정리는 커밋 뒤에 실행해 이 트랜잭션이 네트워크를 기다리지 않게 한다.
            enqueueForCleanup(processed.thumbnailKey());
            return;
        }
        // 다른 워커가 먼저 끝냈다. 썸네일 키는 원본에서 유도되어 둘이 같으므로,
        // 여기서 지우면 살아 있는 행이 가리키는 객체를 지우게 된다. 그냥 물러난다.
        if (file.getStatus() != UploadStatus.PROCESSING) {
            return;
        }
        file.completeProcessing(processed);
        fileRepository.save(file);
        eventPublisher.publishEvent(new MediaReadyEvent(file.getRoomId(), file.getId()));
    }

    @Transactional
    public void markFailed(Long mediaId) {
        fileRepository.findByIdForUpdate(mediaId)
            .filter(found -> found.getStatus() == UploadStatus.PROCESSING)
            .ifPresent(found -> {
                found.failUpload();
                fileRepository.save(found);
            });
    }

    private void enqueueForCleanup(StorageKey thumbnailKey) {
        if (thumbnailKey == null) {
            return;
        }
        List<StorageKey> orphaned = List.of(thumbnailKey);
        orphanObjectCollector.enqueue(orphaned);
        eventPublisher.publishEvent(new ObjectsOrphanedEvent(orphaned));
    }
}
