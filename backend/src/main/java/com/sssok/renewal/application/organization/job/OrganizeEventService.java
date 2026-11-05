package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.job.OrganizeChangeNotifier.Occupancy;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizeTargetPhoto;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Python → Spring 이벤트 수신. 처리했거나 의도적으로 버린 이벤트는 모두 확인(accepted)한다.
// Python 은 확인을 받지 못하면 영원히 재전송하기 때문이다. 상태는 조건부 UPDATE 로 앞으로만 간다.
// 이 메서드가 끝나 트랜잭션이 커밋된 뒤에야 컨트롤러가 응답한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizeEventService {
    private final OrganizeJobRepository repository;
    private final OrganizeResultValidator validator;
    private final OrganizeJobTransitions transitions;
    private final OrganizeChangeNotifier notifier;
    private final OrganizeJobProperties properties;
    private final ObjectMapper objectMapper;
    @Qualifier("organizeClock")
    private final Clock clock;

    @Transactional
    public OrganizeEventAck handle(OrganizeEvent event) {
        OrganizeEventAck ack = new OrganizeEventAck(true, event.executionId(), event.state());
        Optional<OrganizeExecution> found = repository.findExecutionByExecutionId(event.executionId());
        Optional<OrganizeJob> job = found.flatMap(execution -> repository.findById(execution.jobId()));
        if (found.isEmpty() || job.isEmpty() || !belongsTo(event, found.get(), job.get())) {
            log.warn("사진 정리 이벤트를 폐기합니다. executionId={}, state={}", event.executionId(), event.state());
            return ack;
        }
        if (!job.get().status().isAnalyzing()) {
            return ack;
        }
        switch (event.state()) {
            case "RUNNING" -> running(event, found.get(), job.get());
            case "COMPLETED" -> completed(event, found.get(), job.get());
            case "FAILED" -> transitions.fail(job.get().id(), found.get().id(), OrganizeFailureCode.ANALYSIS_FAILED,
                event.errorCode());
            default -> log.debug("처리하지 않는 이벤트 상태입니다. state={}", event.state());
        }
        return ack;
    }

    private boolean belongsTo(OrganizeEvent event, OrganizeExecution execution, OrganizeJob job) {
        return job.publicId().equals(event.jobId())
            && job.roomId().equals(event.roomId())
            && event.attempt() != null && execution.attempt() == event.attempt()
            && job.kind().name().equals(event.jobType())
            // 재시도 전 실행의 ID·회차도 그 자체로는 유효하다. 현재 실행과 비교해야 늦은 결과를 버릴 수 있다.
            && repository.findLatestExecution(job.id())
                .map(latest -> latest.executionId().equals(execution.executionId())).orElse(false);
    }

    private void running(OrganizeEvent event, OrganizeExecution execution, OrganizeJob job) {
        Instant now = clock.instant();
        if (!repository.markExecutionRunning(execution.id(), now)) {
            return;
        }
        repository.updateProgress(job.id(), event.progressStage(), event.processedCount(), event.totalCount(), now);
        notifier.changed(job.id(), job.roomId(), Occupancy.UNCHANGED);
    }

    private void completed(OrganizeEvent event, OrganizeExecution execution, OrganizeJob job) {
        Instant now = clock.instant();
        Set<Long> targetIds = repository.findPhotos(job.id()).stream()
            .map(OrganizeTargetPhoto::mediaId).collect(Collectors.toSet());
        OrganizeResultValidator.Validation validation = validator.validate(job.kind(), event.result(), targetIds);
        if (!validation.valid()) {
            log.warn("사진 정리 결과 검증에 실패했습니다. jobId={}, reason={}", job.publicId(), validation.reason());
            transitions.fail(job.id(), execution.id(), OrganizeFailureCode.RESULT_INVALID, validation.reason());
            return;
        }
        // 검토 기한과 결과 보관 기한은 분석 완료 시각부터 따로 센다. READY 는 방을 계속 점유한다.
        if (repository.markJobReady(job.id(), validation.version(), serialize(event.result()),
            now.plus(properties.review()), now.plus(properties.retention()), now)) {
            repository.markExecutionFinished(execution.id(), OrganizeExecutionStatus.COMPLETED, now);
            notifier.changed(job.id(), job.roomId(), Occupancy.UNCHANGED);
        }
    }

    private String serialize(JsonNode result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("사진 정리 결과를 직렬화할 수 없습니다", failure);
        }
    }
}
