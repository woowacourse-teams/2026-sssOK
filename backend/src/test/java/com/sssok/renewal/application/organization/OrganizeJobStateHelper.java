package com.sssok.renewal.application.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.organization.job.OrganizeCancelService;
import com.sssok.renewal.application.organization.job.OrganizeEvent;
import com.sssok.renewal.application.organization.job.OrganizeEventService;
import com.sssok.renewal.application.organization.job.OrganizeJobTransitions;
import com.sssok.renewal.domain.organization.OrganizeExecution;
import com.sssok.renewal.domain.organization.OrganizeJob;
import com.sssok.renewal.outgoing.database.OrganizeJobRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

// 서비스 테스트가 "분석 서버가 접수한 뒤"나 "검토 중" 같은 상태를 만들 때 쓰는 도우미.
// 상태를 SQL 로 바꾸지 않고 실제 서비스(발송 결과 반영·이벤트 수신·취소)를 거쳐 만든다.
@TestComponent
@RequiredArgsConstructor
public class OrganizeJobStateHelper {
    private final OrganizeEventService eventService;
    private final OrganizeCancelService cancelService;
    private final OrganizeJobTransitions transitions;
    private final OrganizeJobRepository repository;
    private final ObjectMapper objectMapper;

    // 분석 서버가 접수한 상태(RUNNING)로 만든다.
    public void accept(OrganizeJob job) {
        OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();
        transitions.accepted(job.id(), execution.id());
    }

    public void cancelAfterAccepted(OrganizeJob job, Long memberId) {
        accept(job);
        cancelService.cancel(job.roomId(), memberId, job.publicId());
    }

    // 대상 사진 전부를 인물 그룹 하나로 묶은 완료 이벤트를 받아 READY 로 만든다.
    public void ready(OrganizeJob job) {
        List<Long> mediaIds = repository.findPhotos(job.id()).stream().map(photo -> photo.mediaId()).toList();
        ready(job, objectMapper.valueToTree(Map.of(
            "groups", List.of(Map.of("groupId", "person_0001", "mediaIds", mediaIds,
                "faceMembershipCount", mediaIds.size(), "bodyAdditionCount", 0)),
            "unassignedMediaIds", List.of(), "errors", List.of(), "counts", Map.of(), "pipelineVersion", "v1")));
    }

    public void ready(OrganizeJob job, JsonNode result) {
        accept(job);
        OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();
        eventService.handle(new OrganizeEvent(job.publicId(), job.roomId(), execution.executionId(),
            execution.attempt(), job.kind().name(), "COMPLETED", objectMapper.createObjectNode(), result, null));
    }

    // 분석 서버가 실패로 끝냈다고 알려 온 상태(ANALYSIS_FAILED)로 만든다.
    public void analysisFailed(OrganizeJob job) {
        accept(job);
        OrganizeExecution execution = repository.findLatestExecution(job.id()).orElseThrow();
        eventService.handle(new OrganizeEvent(job.publicId(), job.roomId(), execution.executionId(),
            execution.attempt(), job.kind().name(), "FAILED", objectMapper.createObjectNode(), null,
            "SERVER_RESTARTED"));
    }
}
