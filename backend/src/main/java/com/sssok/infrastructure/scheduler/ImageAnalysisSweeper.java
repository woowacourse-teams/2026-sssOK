package com.sssok.infrastructure.scheduler;

import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.search.ImageAnalysisDispatcher;
import com.sssok.infrastructure.config.ImageSearchProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class ImageAnalysisSweeper {
    private final MediaSearchDocumentRepository documentRepository;
    private final ImageAnalysisDispatcher dispatcher;
    private final ImageSearchProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${media.search.sweep-delay:60000}",
        initialDelayString = "${media.search.sweep-delay:60000}")
    public void sweep() {
        documentRepository.registerMissing(properties.createdSince(), properties.batchSize());
        // 등록 행의 DEFAULT CURRENT_TIMESTAMP보다 뒤의 시각으로 대기 작업을 조회한다.
        Instant now = clock.instant();
        documentRepository.recover(now.minus(properties.stuckAfter()), now, properties.maxAttempts());
        documentRepository.findPending(now, properties.batchSize()).forEach(dispatcher::submit);
    }
}
