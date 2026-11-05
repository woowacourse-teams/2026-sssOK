package com.sssok.renewal.incoming.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.renewal.application.organization.job.OrganizeEvent;
import java.util.UUID;

// Python 이 보내는 본문. 모르는 필드(eventId, 시각 등)는 무시한다. 중복 판단은 eventId 가 아니라 executionId 와 종료 상태 기준이다.
public record GalleryAnalysisEventRequest(UUID jobId, Long roomId, UUID executionId, Integer attempt,
                                          String jobType, String state, JsonNode progress, JsonNode result,
                                          String errorCode) {
    public OrganizeEvent toEvent() {
        return new OrganizeEvent(jobId, roomId, executionId, attempt, jobType, state, progress, result, errorCode);
    }
}
