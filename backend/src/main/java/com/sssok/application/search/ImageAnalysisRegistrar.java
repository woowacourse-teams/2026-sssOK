package com.sssok.application.search;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.infrastructure.config.ImageSearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class ImageAnalysisRegistrar {
    private final MediaSearchDocumentRepository documentRepository;
    private final FileRepository fileRepository;
    private final ImageSearchProperties properties;

    // AFTER_COMMIT 호출과 별도 트랜잭션에서 등록을 확정한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(Long mediaId) {
        fileRepository.findById(mediaId)
            .filter(file -> !file.getCreatedAt().isBefore(properties.createdSince()))
            .ifPresent(file -> documentRepository.register(mediaId));
    }
}
