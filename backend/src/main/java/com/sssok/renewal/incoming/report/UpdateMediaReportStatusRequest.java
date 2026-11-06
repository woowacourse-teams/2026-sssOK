package com.sssok.renewal.incoming.report;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateMediaReportStatusRequest(
    @Schema(description = "UNREFLECTED(미처리)·REFLECTED(처리완료) 중 하나") String status
) {
}
