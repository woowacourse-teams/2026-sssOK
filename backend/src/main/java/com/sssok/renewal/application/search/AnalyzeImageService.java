package com.sssok.renewal.application.search;

import com.sssok.renewal.outgoing.database.UploadFileRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import com.sssok.renewal.outgoing.ai.OpenAiImageDescriptionClient;
import com.sssok.renewal.outgoing.ai.OpenAiImageDescriptionClient.Description;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository;
import com.sssok.renewal.outgoing.database.MediaSearchDocumentRepository.AnalysisAttempt;
import com.sssok.renewal.outgoing.ai.OpenAiTextEmbeddingClient;
import com.sssok.renewal.outgoing.ai.OpenAiTextEmbeddingClient.Embedding;
import com.sssok.renewal.application.search.exception.AiCallException;
import com.sssok.domain.file.DerivativeContentType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.search.ImageAnalysis;
import com.sssok.renewal.outgoing.config.ImageSearchProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이미지 설명을 만든 뒤, 설명을 검색용 벡터(임베딩)로 변환해 저장한다.
 * 설명은 중간 저장하므로 임베딩만 실패하면 설명 생성부터 다시 호출하지 않는다.
 * 외부 호출 중 서버가 멈추면 응답 수신 여부를 알 수 없어 무조건 재호출하지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class AnalyzeImageService {
    private final MediaSearchDocumentRepository analysisRepository;
    private final UploadFileRepository uploadFileRepository;
    private final FileStorage fileStorage;
    private final OpenAiImageDescriptionClient imageDescriptionClient;
    private final OpenAiTextEmbeddingClient textEmbeddingClient;
    private final ImageSearchProperties properties;
    private final Clock clock;

    // 외부 AI 호출은 트랜잭션 밖에서 실행한다. repository의 짧은 트랜잭션만 사용한다.
    public void analyze(Long mediaId) {
        Optional<AnalysisAttempt> claimedAttempt =
            analysisRepository.claim(mediaId, clock.instant(), properties.maxAttempts());
        if (claimedAttempt.isEmpty()) {
            return;
        }
        AnalysisAttempt analysisAttempt = claimedAttempt.get();
        Optional<Description> description = findOrGenerateAndSaveDescription(analysisAttempt);
        if (description.isEmpty()) {
            return;
        }
        generateEmbeddingAndComplete(analysisAttempt, description.get());
    }

    // 결과가 비어 있으면 대상 삭제·소유권 상실·실패로 이번 실행을 중단한다.
    private Optional<Description> findOrGenerateAndSaveDescription(AnalysisAttempt analysisAttempt) {
        FailureStage failureStage = FailureStage.DATABASE;
        try {
            StoredFile storedFile = uploadFileRepository.findById(analysisAttempt.mediaId()).orElse(null);
            if (storedFile == null) {
                return Optional.empty();
            }
            Optional<Description> savedDescription = analysisRepository.findDescription(analysisAttempt);
            if (savedDescription.isPresent()) {
                return savedDescription;
            }

            // 저장된 설명이 없을 때만 AI가 읽을 이미지 URL을 발급한다.
            failureStage = FailureStage.STORAGE;
            String imageUrl = createAnalysisImageUrl(storedFile);
            // 호출 전에 DB에 기록한다. 기록이 거절되면 AI를 호출하지 않는다.
            failureStage = FailureStage.DATABASE;
            if (!analysisRepository.beginExternalCall(analysisAttempt, clock.instant())) {
                return Optional.empty();
            }
            failureStage = FailureStage.EXTERNAL;
            Description generatedDescription = imageDescriptionClient.describe(imageUrl, properties.requestTimeout());
            failureStage = FailureStage.VALIDATION;
            validateDescription(generatedDescription);
            failureStage = FailureStage.DATABASE;
            // 설명을 먼저 커밋해 두어 임베딩 재시도에서 설명 생성 호출을 반복하지 않는다.
            if (!analysisRepository.saveDescription(analysisAttempt, generatedDescription, clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(generatedDescription);
        } catch (RuntimeException failure) {
            recordAnalysisFailure(analysisAttempt, failure, failureStage);
            return Optional.empty();
        }
    }

    private void generateEmbeddingAndComplete(AnalysisAttempt analysisAttempt, Description description) {
        FailureStage failureStage = FailureStage.VALIDATION;
        try {
            validateDescription(description);
            String searchText = description.description() + "\n" + description.features();
            failureStage = FailureStage.DATABASE;
            if (!analysisRepository.beginExternalCall(analysisAttempt, clock.instant())) {
                return;
            }
            failureStage = FailureStage.EXTERNAL;
            // 설명과 특징을 숫자 벡터로 변환해 유사 이미지 검색에 사용한다.
            Embedding embedding = textEmbeddingClient.embed(searchText, properties.requestTimeout());
            failureStage = FailureStage.VALIDATION;
            ImageAnalysis analysis = createAnalysisResult(description, searchText, embedding);
            failureStage = FailureStage.DATABASE;
            analysisRepository.complete(analysisAttempt, analysis, clock.instant());
        } catch (RuntimeException failure) {
            recordAnalysisFailure(analysisAttempt, failure, failureStage);
        }
    }

    private ImageAnalysis createAnalysisResult(Description description, String searchText, Embedding embedding) {
        if (embedding == null) {
            throw new IllegalArgumentException("임베딩 결과가 필요합니다");
        }
        return new ImageAnalysis(description.description(), description.features(), searchText,
            embedding.values(), embedding.model(), description.model(), description.promptVersion());
    }

    // 같은 예외라도 발생 위치에 따라 재시도 가능 여부가 달라진다.
    // 각 작업 직전에 갱신하며, 공유 필드로 두지 않아 동시 실행 간 상태가 섞이지 않는다.
    private enum FailureStage {
        STORAGE,    // AI 호출 전 이미지 URL 발급
        DATABASE,   // 작업 상태·중간 결과·최종 결과 저장
        EXTERNAL,   // AI 호출: 실패 시 실제 처리 여부가 불명확할 수 있음
        VALIDATION  // AI 응답 내용 검증
    }

    private String createAnalysisImageUrl(StoredFile file) {
        StorageKey previewKey = file.getPreviewKey();
        StorageKey analysisImageKey = previewKey == null ? file.getStorageKey() : previewKey;
        String contentType = previewKey == null
            ? file.getMediaType().contentType() : DerivativeContentType.of(previewKey);
        return fileStorage.presignGet(analysisImageKey, "inline", contentType, properties.imageUrlTtl());
    }

    private void validateDescription(Description description) {
        if (description == null || description.description() == null || description.description().isBlank()
            || description.features() == null || description.features().isBlank()
            || description.model() == null || description.model().isBlank()
            || description.promptVersion() == null || description.promptVersion().isBlank()) {
            throw new IllegalArgumentException("이미지 설명과 특징이 필요합니다");
        }
    }

    private void recordAnalysisFailure(AnalysisAttempt analysisAttempt, RuntimeException exception, FailureStage stage) {
        boolean retryable = isRetryable(exception, stage);
        String errorCode = resolveFailureCode(exception, stage);
        // 제공자 응답·서명 URL·설명 본문은 기록하지 않는다.
        log.warn("이미지 분석 실패. mediaId={}, attempt={}, stage={}, errorType={}, errorCode={}, retryable={}",
            analysisAttempt.mediaId(), analysisAttempt.attemptNumber(), stage, exception.getClass().getSimpleName(), errorCode, retryable);
        Instant now = clock.instant();
        Instant retryAt = calculateRetryAt(analysisAttempt, exception, now, retryable);
        try {
            analysisRepository.fail(analysisAttempt, errorCode, now, retryAt, properties.maxAttempts(), retryable);
        } catch (RuntimeException persistenceFailure) {
            // 호출 중 표시를 유지한다. 복구 배치가 불명확한 호출을 재실행하지 않고 종료한다.
            log.warn("이미지 분석 실패 기록 실패. mediaId={}, attempt={}, errorType={}",
                analysisAttempt.mediaId(), analysisAttempt.attemptNumber(), persistenceFailure.getClass().getSimpleName());
        }
    }

    private boolean isRetryable(RuntimeException exception, FailureStage stage) {
        if (exception instanceof AiCallException failure) {
            return failure.retryDisposition() == AiCallException.RetryDisposition.SAFE_TO_RETRY;
        }
        // 서명 실패는 외부 AI 호출 전에 발생하므로 재시도할 수 있다.
        return stage == FailureStage.STORAGE;
    }

    private String resolveFailureCode(RuntimeException exception, FailureStage stage) {
        if (exception instanceof AiCallException failure) {
            return failure.retryDisposition() == AiCallException.RetryDisposition.UNKNOWN_OUTCOME
                ? "UNKNOWN_EXTERNAL_OUTCOME" : failure.code();
        }
        return switch (stage) {
            case STORAGE -> "STORAGE_FAILED";
            case DATABASE -> "PERSISTENCE_FAILED";
            case VALIDATION -> "INVALID_RESPONSE";
            case EXTERNAL -> "UNKNOWN_EXTERNAL_OUTCOME";
        };
    }

    private Instant calculateRetryAt(AnalysisAttempt analysisAttempt, RuntimeException exception, Instant now, boolean retryable) {
        if (!retryable) {
            return now;
        }
        // 시도마다 대기 시간을 2배로 늘리되 배수는 64에서 제한한다.
        long backoffMultiplier = 1L << Math.min(analysisAttempt.attemptNumber() - 1, 6);
        Instant retryAt = now.plus(properties.retryDelay().multipliedBy(backoffMultiplier));
        // 제공자가 더 늦은 재시도 시각을 지정하면 그 시각을 우선한다.
        if (exception instanceof AiCallException failure && failure.retryNotBefore() != null
            && failure.retryNotBefore().isAfter(retryAt)) {
            return failure.retryNotBefore();
        }
        return retryAt;
    }

}
