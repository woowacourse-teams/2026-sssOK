package com.sssok.renewal.incoming.report;

import com.sssok.renewal.domain.report.MediaReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ReportMediaResponse(
    @Schema(description = "신고한 미디어 ID") Long mediaId,
    @Schema(description = "신고 사유") String reason,
    @Schema(description = "접수 시각") Instant reportedAt
) {
    public static ReportMediaResponse from(MediaReport report) {
        return new ReportMediaResponse(report.getMediaId(), report.getReason().name(), report.getReportedAt());
    }
}
