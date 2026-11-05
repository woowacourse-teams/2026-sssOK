package com.sssok.renewal.application.organization.job;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.StorageKey;
import com.sssok.renewal.application.organization.execute.AnalysisPhoto;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionException;
import com.sssok.renewal.application.organization.execute.OrganizeExecutionRequest;
import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeExecutionStatus;
import com.sssok.renewal.domain.organization.OrganizeFailureCode;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.domain.organization.OrganizePhotoSource;
import com.sssok.renewal.domain.organization.OrganizeTargetPhoto;
import com.sssok.renewal.outgoing.ai.McpOrganizeToolExecutor;
import com.sssok.renewal.outgoing.config.OrganizeJobProperties;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import com.sssok.renewal.outgoing.storage.FileStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

// 저장된 실행을 MCP 에 보낸다. 커밋 직후 한 번 시도하고, 못 보낸 것은 스위퍼가 다시 집어 간다.
// 외부 호출과 서명 URL 발급 중에는 DB 트랜잭션을 잡지 않는다. 각 DB 변경은 짧은 조건부 UPDATE 하나다.
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizeDispatchService {
    private static final int SWEEP_BATCH = 20;

    private final OrganizeJobProperties properties;
    private final OrganizeJobRepository repository;
    private final OrganizeJobTransitions transitions;
    private final FileRepository fileRepository;
    private final FileStorage fileStorage;
    private final ObjectProvider<McpOrganizeToolExecutor> executor;
    @Qualifier("organizeClock")
    private final Clock clock;

    public void dispatchDue() {
        if (!properties.enabled()) {
            return;
        }
        for (Long executionPk : repository.findDispatchableExecutionIds(clock.instant(), SWEEP_BATCH)) {
            dispatch(executionPk);
        }
    }

    public void dispatch(Long executionPk) {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        if (!repository.claimExecution(executionPk, now, now.plus(properties.dispatch().lease()))) {
            return;
        }
        Optional<OrganizeExecution> execution = repository.findExecutionById(executionPk);
        Optional<OrganizeJob> job = execution.flatMap(found -> repository.findById(found.jobId()));
        if (job.isPresent()) {
            send(job.get(), execution.get(), now);
        }
    }

    // 이미 끝난 작업의 실행은 보내지 않고 닫는다. 발송 기한이 지났으면 실패로 닫고, 아니면 MCP 로 보낸다.
    private void send(OrganizeJob job, OrganizeExecution execution, Instant now) {
        if (!job.status().isAnalyzing()) {
            transitions.discard(execution.id());
            return;
        }
        if (now.isAfter(execution.dispatchDeadlineAt())) {
            transitions.fail(job.id(), execution.id(), OrganizeFailureCode.DISPATCH_TIMEOUT, null);
            return;
        }
        McpOrganizeToolExecutor toolExecutor = executor.getIfAvailable();
        if (toolExecutor == null) {
            log.warn("사진 정리 MCP 실행기가 없어 발송을 미룹니다. jobId={}", job.publicId());
            retryLater(execution, OrganizeExecutionStatus.DISPATCH_PENDING);
            return;
        }
        Optional<OrganizeExecutionRequest> request = buildRequest(job, execution);
        if (request.isEmpty()) {
            transitions.fail(job.id(), execution.id(), OrganizeFailureCode.TARGET_CHANGED, null);
            return;
        }
        try {
            toolExecutor.execute(request.get());
            transitions.accepted(job.id(), execution.id());
        } catch (OrganizeExecutionException failure) {
            handleFailure(job, execution, failure.reason());
        }
    }

    private void handleFailure(OrganizeJob job, OrganizeExecution execution,
                               OrganizeExecutionException.Reason reason) {
        switch (reason) {
            // SERVER_BUSY 는 서버가 실행을 만들기 전에 거절한 경우라 같은 실행 ID 로 다시 보내도 안전하다.
            case NOT_DELIVERED, SERVER_BUSY -> retryLater(execution, OrganizeExecutionStatus.DISPATCH_PENDING);
            case OUTCOME_UNKNOWN, INVALID_RESPONSE -> retryLater(execution, OrganizeExecutionStatus.DISPATCH_UNKNOWN);
            case AUTHENTICATION_FAILED -> {
                log.error("사진 정리 MCP 인증에 실패했습니다. 토큰 설정을 확인하세요. jobId={}", job.publicId());
                transitions.fail(job.id(), execution.id(), OrganizeFailureCode.AUTHENTICATION_FAILED, null);
            }
            case TOOL_CHANGED -> transitions.fail(job.id(), execution.id(), OrganizeFailureCode.TOOL_CHANGED, null);
            case REJECTED -> transitions.fail(job.id(), execution.id(), OrganizeFailureCode.REJECTED, null);
        }
    }

    private void retryLater(OrganizeExecution execution, OrganizeExecutionStatus status) {
        Instant now = clock.instant();
        Instant next = now.plus(properties.dispatch().retryDelayAfter(execution.dispatchAttempts()));
        repository.scheduleDispatchRetry(execution.id(), status, next, now);
    }

    // 서명 URL 은 DB 에 저장하지 않고 발송 때마다 새로 발급한다. 대상 사진이 사라졌으면 비어 있다.
    private Optional<OrganizeExecutionRequest> buildRequest(OrganizeJob job, OrganizeExecution execution) {
        List<OrganizeTargetPhoto> targets = repository.findPhotos(job.id());
        Map<Long, StoredFile> files = fileRepository.findAllByIdIn(targets.stream().map(OrganizeTargetPhoto::mediaId).toList())
            .stream()
            .collect(Collectors.toMap(StoredFile::getId, Function.identity()));

        List<AnalysisPhoto> photos = new ArrayList<>();
        for (OrganizeTargetPhoto target : targets) {
            StoredFile file = files.get(target.mediaId());
            if (file == null || !file.getRoomId().equals(job.roomId())) {
                return Optional.empty();
            }
            StorageKey key = sourceKeyOf(file, target.source());
            String url = fileStorage.presignGet(key, "inline", null, properties.photoUrlTtl());
            photos.add(new AnalysisPhoto(target.mediaId(), url, extensionOf(key)));
        }
        OrganizeTool tool = new OrganizeTool(job.serverId(), job.toolName(), job.toolDescription());
        return Optional.of(new OrganizeExecutionRequest(tool, job.roomId(), job.publicId(), execution.executionId(),
            execution.attempt(), photos));
    }

    // 프리뷰가 없는 사진은 원본으로 대신한다.
    private StorageKey sourceKeyOf(StoredFile file, OrganizePhotoSource source) {
        if (source == OrganizePhotoSource.PREVIEW && file.getPreviewKey() != null) {
            return file.getPreviewKey();
        }
        return file.getStorageKey();
    }

    private String extensionOf(StorageKey key) {
        String value = key.value();
        return value.substring(value.lastIndexOf('.') + 1).toLowerCase();
    }
}
