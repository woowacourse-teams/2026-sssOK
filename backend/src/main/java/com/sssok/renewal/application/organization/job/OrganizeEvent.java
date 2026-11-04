package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

// Python 이 보낸 이벤트. 원본 URL·파일 경로는 오지 않는다.
public record OrganizeEvent(UUID jobId, Long roomId, UUID executionId, Integer attempt, String jobType,
                            String state, JsonNode progress, JsonNode result, String errorCode) {

    public String progressStage() {
        if (progress == null || !progress.path("stage").isTextual()) {
            return null;
        }
        return progress.path("stage").asText();
    }

    public Integer processedCount() {
        return progressCount("processed");
    }

    public Integer totalCount() {
        return progressCount("total");
    }

    private Integer progressCount(String field) {
        if (progress == null) {
            return null;
        }
        JsonNode count = progress.path("counts").path(field);
        return count.isIntegralNumber() ? count.asInt() : null;
    }
}
