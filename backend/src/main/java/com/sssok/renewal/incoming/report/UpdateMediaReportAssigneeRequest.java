package com.sssok.renewal.incoming.report;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateMediaReportAssigneeRequest(
    @Schema(description = "MAICHAN(마이찬)·HAENI(해니)·YUNDOL(윤돌)·HYUNMIBAP(현미밥) 중 하나. null 이면 담당자를 비운다",
        nullable = true)
    String assignee
) {
}
