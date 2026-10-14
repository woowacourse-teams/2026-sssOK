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
        Instant now = clock.instant();
        documentRepository.registerMissing(properties.createdSince(), now, properties.batchSize());
        documentRepository.recover(now.minus(properties.stuckAfter()), now, properties.maxAttempts());
        documentRepository.findPending(now, properties.batchSize()).forEach(dispatcher::submit);
    }
}
