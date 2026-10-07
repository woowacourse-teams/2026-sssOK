package com.sssok.application.port.out;

import com.sssok.domain.search.ImageAnalysis;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaSearchDocumentRepository {
    void register(Long mediaId, Instant now);

    int registerMissing(Instant createdSince, Instant now, int limit);

    Optional<AnalysisAttempt> claim(Long mediaId, Instant now, int maxAttempts);

    boolean complete(AnalysisAttempt attempt, ImageAnalysis analysis, Instant now);

    boolean fail(AnalysisAttempt attempt, String errorCode, Instant now, Instant retryAt, int maxAttempts, boolean retryable);

    Optional<ImageDescriptionPort.Description> findDescription(AnalysisAttempt attempt);

    boolean beginExternalCall(AnalysisAttempt attempt, Instant now);

    boolean saveDescription(AnalysisAttempt attempt, ImageDescriptionPort.Description description, Instant now);

    int recover(Instant stuckBefore, Instant now, int maxAttempts);

    List<Long> findPending(Instant now, int limit);

    // 시도 번호로 이전 워커의 늦은 결과가 새 실행을 덮어쓰지 못하게 한다.
    record AnalysisAttempt(Long mediaId, int attemptNumber) {
    }
}
