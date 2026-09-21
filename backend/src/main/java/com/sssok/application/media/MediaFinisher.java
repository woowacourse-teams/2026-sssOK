package com.sssok.application.media;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 워커 결과를 DB 에 반영하는 부분만 짧게 트랜잭션으로 감싼다.
// GenerateThumbnailService 안에서 직접 호출하면 프록시를 타지 않아 @Transactional 이 걸리지 않으므로
// 별도 빈으로 둔다.
@Component
@RequiredArgsConstructor
public class MediaFinisher {

    private final FileRepository fileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void finish(StoredFile file, ProcessedMedia processed) {
        file.completeProcessing(processed);
        StoredFile saved = fileRepository.save(file);
        eventPublisher.publishEvent(new MediaReadyEvent(saved.getRoomId(), saved.getId()));
    }

    @Transactional
    public void markFailed(StoredFile file) {
        file.failUpload();
        fileRepository.save(file);
    }
}
