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

    // DB 작업만 짧게 커밋한다. 외부 호출 시작 기록과 체크포인트는 재시도에도 유지된다.
    public void analyze(Long mediaId) {
        Optional<AnalysisAttempt> claimed = documentRepository.claim(mediaId, clock.instant(), properties.maxAttempts());
        if (claimed.isEmpty()) {
            return;
        }
        AnalysisAttempt attempt = claimed.get();
        FailureStage stage = FailureStage.DATABASE;
        try {
            StoredFile file = fileRepository.findById(mediaId).orElse(null);
            if (file == null) {
                return;
            }
            Description description = documentRepository.findDescription(attempt).orElse(null);
            if (description == null) {
                stage = FailureStage.STORAGE;
                String imageUrl = createImageUrl(file);
                stage = FailureStage.DATABASE;
                if (!documentRepository.beginExternalCall(attempt, clock.instant())) {
                    return;
                }
                stage = FailureStage.EXTERNAL;
                description = imageDescriptionPort.describe(imageUrl, properties.requestTimeout());
                stage = FailureStage.VALIDATION;
                requireDescription(description);
                stage = FailureStage.DATABASE;
                if (!documentRepository.saveDescription(attempt, description, clock.instant())) {
                    return; // 삭제되거나 선점권을 잃은 실행은 다음 외부 호출을 하지 않는다.
                }
            }
            stage = FailureStage.VALIDATION;
            requireDescription(description);
            String searchText = description.description() + "\n" + description.features();
            stage = FailureStage.DATABASE;
            if (!documentRepository.beginExternalCall(attempt, clock.instant())) {
                return;
            }
            stage = FailureStage.EXTERNAL;
            Embedding embedding = textEmbeddingPort.embed(searchText, properties.requestTimeout());
            stage = FailureStage.VALIDATION;
            if (embedding == null) {
                throw new IllegalArgumentException("임베딩 결과가 필요합니다");
            }
            ImageAnalysis analysis = new ImageAnalysis(description.description(), description.features(), searchText,
                embedding.values(), embedding.model(), description.model(), description.promptVersion());
            stage = FailureStage.DATABASE;
            documentRepository.complete(attempt, analysis, clock.instant());
        } catch (RuntimeException exception) {
            recordFailure(attempt, exception, stage);
        }
    }

    private enum FailureStage {
        STORAGE, DATABASE, EXTERNAL, VALIDATION
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
            || description.features() == null || description.features().isBlank()
            || description.model() == null || description.model().isBlank()
            || description.promptVersion() == null || description.promptVersion().isBlank()) {
            throw new IllegalArgumentException("이미지 설명과 특징이 필요합니다");
        }
    }

    private void recordFailure(AnalysisAttempt attempt, RuntimeException exception, FailureStage stage) {
        boolean retryable = false;
        String errorCode;
        if (exception instanceof AiCallException failure) {
            retryable = failure.retryDisposition() == AiCallException.RetryDisposition.SAFE_TO_RETRY;
            errorCode = failure.retryDisposition() == AiCallException.RetryDisposition.UNKNOWN_OUTCOME
                ? "UNKNOWN_EXTERNAL_OUTCOME" : failure.code();
        } else {
            errorCode = switch (stage) {
                case STORAGE -> "STORAGE_FAILED";
                case DATABASE -> "PERSISTENCE_FAILED";
                case VALIDATION -> "INVALID_RESPONSE";
                case EXTERNAL -> "UNKNOWN_EXTERNAL_OUTCOME";
            };
            // 서명 URL 발급 실패는 외부 AI 호출 전에 발생하므로 재시도할 수 있다.
            retryable = stage == FailureStage.STORAGE;
        }
        // 제공자 응답·서명 URL·설명 본문은 기록하지 않는다.
        log.warn("이미지 분석 실패. mediaId={}, attempt={}, stage={}, errorType={}, errorCode={}, retryable={}",
            attempt.mediaId(), attempt.attemptNumber(), stage, exception.getClass().getSimpleName(), errorCode, retryable);
        Instant now = clock.instant();
        long multiplier = 1L << Math.min(attempt.attemptNumber() - 1, 6);
        Instant retryAt = retryable ? now.plus(properties.retryDelay().multipliedBy(multiplier)) : now;
        if (retryable && exception instanceof AiCallException failure && failure.retryNotBefore() != null
            && failure.retryNotBefore().isAfter(retryAt)) {
            retryAt = failure.retryNotBefore();
        }
        try {
            documentRepository.fail(attempt, errorCode, now, retryAt, properties.maxAttempts(), retryable);
        } catch (RuntimeException persistenceFailure) {
            // 호출 중 표시를 유지한다. 복구 배치가 불명확한 호출을 재실행하지 않고 종료한다.
            log.warn("이미지 분석 실패 기록 실패. mediaId={}, attempt={}, errorType={}",
                attempt.mediaId(), attempt.attemptNumber(), persistenceFailure.getClass().getSimpleName());
        }
    }
}
