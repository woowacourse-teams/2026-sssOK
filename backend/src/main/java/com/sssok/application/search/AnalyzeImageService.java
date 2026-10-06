package com.sssok.application.search;

import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.search.ImageAnalysis;
import com.sssok.infrastructure.config.ImageSearchProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AnalyzeImageService {
    private final MediaSearchDocumentRepository documents;
    private final FileRepository files;
    private final FileStoragePort storage;
    private final ImageDescriptionPort descriptions;
    private final TextEmbeddingPort embeddings;
    private final ImageSearchProperties properties;
    private final Clock clock;

    // 외부 호출을 감싸는 트랜잭션은 없다. 저장 어댑터의 각 호출만 짧게 커밋한다.
    public void analyze(Long mediaId) {
        var attempt = documents.claim(mediaId, clock.instant(), properties.maxAttempts());
        if (attempt.isEmpty()) {
            return;
        }
        try {
            var file = files.findById(mediaId).orElse(null);
            if (file == null) {
                return; // 원본 삭제 시 검색 문서도 CASCADE로 삭제된다.
            }
            StorageKey key = file.getPreviewKey() == null ? file.getStorageKey() : file.getPreviewKey();
            String contentType = file.getPreviewKey() == null
                ? file.getMediaType().contentType() : DerivativeContentType.of(key);
            String url = storage.presignGet(key, "inline", contentType, properties.imageUrlTtl());
            var description = descriptions.describe(url, properties.requestTimeout());
            if (description.description() == null || description.description().isBlank()
                || description.features() == null || description.features().isBlank()) {
                throw new IllegalArgumentException("이미지 설명과 특징이 필요합니다");
            }
            String text = description.description() + "\n" + description.features();
            var embedding = embeddings.embed(text, properties.requestTimeout());
            var result = new ImageAnalysis(description.description(), description.features(), text,
                embedding.values(), embedding.model(), description.model(), description.promptVersion());
            documents.complete(attempt.get(), result, clock.instant());
        } catch (RuntimeException exception) {
            // 외부 응답·서명 URL·사진 설명이 오류 메시지에 포함될 수 있어 예외 본문은 기록하지 않는다.
            log.warn("이미지 분석 실패. mediaId={}, attempt={}, errorType={}", mediaId,
                attempt.get().attemptNumber(), exception.getClass().getSimpleName());
            documents.fail(attempt.get(), "ANALYSIS_FAILED",
                clock.instant().plus(properties.retryDelay()), properties.maxAttempts());
        }
    }
}
