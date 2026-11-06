package com.sssok.renewal.application.organization.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;

// Python 이 보낸 이벤트. 원본 URL·파일 경로는 오지 않는다.
public record OrganizeEvent(UUID jobId, Long roomId, UUID executionId, Integer attempt, String jobType,
                            String state, JsonNode progress, JsonNode result, String errorCode) {
    private static final Set<String> UNITS = Set.of("PHOTO", "BODY");

    // 저장·비교에 쓰는 진행 정보. 잘못된 값은 버리고 단계만 남긴다(분석을 실패시킬 이유가 아니다).
    public record Snapshot(String stage, Integer processed, Integer total, String unit) {
    }

    public Snapshot snapshot() {
        Integer processed = count("processed");
        Integer total = count("total");
        boolean valid = processed != null && total != null && processed >= 0 && processed <= total;
        return new Snapshot(text("stage"), valid ? processed : null, valid ? total : null,
            valid ? unit() : null);
    }

    private String text(String field) {
        if (progress == null || !progress.path(field).isTextual()) {
            return null;
        }
        return progress.path(field).asText();
    }

    private String unit() {
        String unit = text("unit");
        return unit != null && UNITS.contains(unit) ? unit : null;
    }

    // 현재 형식은 progress 바로 아래(processed·total)이고, 이전 Python 은 progress.counts 아래로 보냈다.
    private Integer count(String field) {
        if (progress == null) {
            return null;
        }
        JsonNode flat = progress.path(field);
        if (flat.isIntegralNumber()) {
            return flat.asInt();
        }
        JsonNode legacy = progress.path("counts").path(field);
        return legacy.isIntegralNumber() ? legacy.asInt() : null;
    }
}
