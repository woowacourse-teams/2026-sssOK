package com.sssok.infrastructure.persistence.search;

import com.sssok.application.port.out.MediaSearchDocumentRepository;
import com.sssok.application.port.out.ImageDescriptionPort.Description;
import com.sssok.domain.search.ImageAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional
public class MediaSearchDocumentRepositoryAdapter implements MediaSearchDocumentRepository {
    private final MediaSearchDocumentJpaRepository jpaRepository;

    @Override
    public void register(Long mediaId, Instant now) {
        jpaRepository.register(mediaId, now);
    }

    @Override
    public int registerMissing(Instant createdSince, Instant now, int limit) {
        requirePositive(limit);
        return jpaRepository.registerMissing(createdSince, now, limit);
    }

    @Override
    public Optional<AnalysisAttempt> claim(Long mediaId, Instant now, int maxAttempts) {
        requirePositive(maxAttempts);
        if (jpaRepository.claim(now, mediaId, maxAttempts) == 0) {
            return Optional.empty();
        }
        // UPDATE가 잡은 행 잠금은 이 트랜잭션 종료까지 유지되어 시도 번호가 바뀌지 않는다.
        return jpaRepository.findById(mediaId)
            .map(document -> new AnalysisAttempt(mediaId, document.getAttempts()));
    }

    @Override
    public boolean complete(AnalysisAttempt attempt, ImageAnalysis analysis, Instant now) {
        String vector = analysis.embedding().stream().map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
        return jpaRepository.complete(analysis.description(), analysis.features(), analysis.searchText(),
            vector, analysis.embeddingModel(), analysis.embedding().size(), analysis.analysisModel(),
            analysis.promptVersion(), now, attempt.mediaId(), attempt.attemptNumber()) == 1;
    }

    @Override
    public boolean fail(AnalysisAttempt attempt, String errorCode, Instant now, Instant retryAt, int maxAttempts, boolean retryable) {
        requirePositive(maxAttempts);
        return jpaRepository.fail(retryable, maxAttempts, errorCode, now, retryAt,
            attempt.mediaId(), attempt.attemptNumber()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Description> findDescription(AnalysisAttempt attempt) {
        return jpaRepository.findById(attempt.mediaId())
            .filter(document -> "PROCESSING".equals(document.getStatus())
                && document.getAttempts() == attempt.attemptNumber() && document.getDescription() != null)
            .map(document -> new Description(document.getDescription(), document.getFeatures(),
                document.getAnalysisModel(), document.getPromptVersion()));
    }

    @Override
    public boolean beginExternalCall(AnalysisAttempt attempt, Instant now) {
        return jpaRepository.beginExternalCall(attempt.mediaId(), attempt.attemptNumber(), now) == 1;
    }

    @Override
    public boolean saveDescription(AnalysisAttempt attempt, Description description, Instant now) {
        return jpaRepository.saveDescription(attempt.mediaId(), attempt.attemptNumber(), description.description(),
            description.features(), description.model(), description.promptVersion(), now) == 1;
    }

    @Override
    public int recover(Instant stuckBefore, Instant now, int maxAttempts) {
        requirePositive(maxAttempts);
        return jpaRepository.recover(maxAttempts, now, stuckBefore);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findPending(Instant now, int limit) {
        requirePositive(limit);
        return jpaRepository.findPending(now, limit);
    }

    private static void requirePositive(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("작업 제한은 양수여야 합니다");
        }
    }
}
