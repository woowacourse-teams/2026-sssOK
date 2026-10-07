package com.sssok.application.search;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.ImageDescriptionPort.Description;
import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.port.out.MediaSearchDocumentRepository.AnalysisAttempt;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.application.port.out.TextEmbeddingPort.Embedding;
import com.sssok.application.search.exception.AiCallException;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.search.ImageAnalysis;
import com.sssok.infrastructure.config.ImageSearchProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AnalyzeImageService {
    private final MediaSearchDocumentRepository documentRepository;
    private final FileRepository fileRepository;
    private final FileStoragePort storage;
    private final ImageDescriptionPort imageDescriptionPort;
    private final TextEmbeddingPort textEmbeddingPort;
    private final ImageSearchProperties properties;
    private final Clock clock;

    // 외부 호출을 감싸는 트랜잭션은 없다. 저장 어댑터의 각 호출만 짧게 커밋한다.
    public void analyze(Long mediaId) {
        Optional<AnalysisAttempt> claimed = documentRepository.claim(mediaId, clock.instant(), properties.maxAttempts());
        if (claimed.isEmpty()) {
            return;
        }
        AnalysisAttempt attempt = claimed.get();
        try {
            StoredFile file = fileRepository.findById(mediaId).orElse(null);
            if (file == null) {
                return; // 원본 삭제 시 검색 문서도 CASCADE로 삭제된다.
            }
            ImageAnalysis analysis = analyzeFile(file);
            documentRepository.complete(attempt, analysis, clock.instant());
        } catch (RuntimeException exception) {
            recordFailure(attempt, exception);
        }
    }

    private ImageAnalysis analyzeFile(StoredFile file) {
        String imageUrl = createImageUrl(file);
        Description description = imageDescriptionPort.describe(imageUrl, properties.requestTimeout());
        requireDescription(description);
        String searchText = description.description() + "\n" + description.features();
        Embedding embedding = textEmbeddingPort.embed(searchText, properties.requestTimeout());
        return new ImageAnalysis(description.description(), description.features(), searchText,
            embedding.values(), embedding.model(), description.model(), description.promptVersion());
    }

    private String createImageUrl(StoredFile file) {
        StorageKey preview = file.getPreviewKey();
        StorageKey source = preview == null ? file.getStorageKey() : preview;
        String contentType = preview == null
            ? file.getMediaType().contentType() : DerivativeContentType.of(preview);
        return storage.presignGet(source, "inline", contentType, properties.imageUrlTtl());
    }

    private void requireDescription(Description description) {
        if (description == null || description.description() == null || description.description().isBlank()
            || description.features() == null || description.features().isBlank()) {
            throw new IllegalArgumentException("이미지 설명과 특징이 필요합니다");
        }
    }

    private void recordFailure(AnalysisAttempt attempt, RuntimeException exception) {
        // 외부 응답·서명 URL·사진 설명이 포함될 수 있어 예외 본문은 기록하지 않는다.
        String errorCode = exception instanceof AiCallException failure
            ? failure.code() : "ANALYSIS_FAILED";
        log.warn("이미지 분석 실패. mediaId={}, attempt={}, errorType={}, errorCode={}", attempt.mediaId(),
            attempt.attemptNumber(), exception.getClass().getSimpleName(), errorCode);
        Instant now = clock.instant();
        documentRepository.fail(attempt, errorCode, now, now.plus(properties.retryDelay()),
            properties.maxAttempts());
    }
}
