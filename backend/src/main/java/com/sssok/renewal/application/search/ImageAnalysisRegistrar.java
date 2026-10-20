package com.sssok.renewal.application.search;

import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository;
import com.sssok.renewal.outgoing.config.ImageSearchProperties;
import lombok.RequiredArgsConstructor;
import java.time.Clock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class ImageAnalysisRegistrar {
    private final MediaSearchDocumentRepository documentRepository;
    private final UploadFileRepository fileRepository;
    private final ImageSearchProperties properties;
    private final Clock clock;

    // AFTER_COMMIT 호출과 별도 트랜잭션에서 등록을 확정한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(Long mediaId) {
        fileRepository.findById(mediaId)
            .filter(file -> !file.getCreatedAt().isBefore(properties.createdSince()))
            .ifPresent(file -> documentRepository.register(mediaId, clock.instant()));
    }
}
