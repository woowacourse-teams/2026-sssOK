package com.sssok.renewal.incoming.report;

import com.sssok.renewal.application.report.MediaReportPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MediaReportsResponse(
    @Schema(description = "신고 목록 (접수 최신순)") List<MediaReportSummaryResponse> reports,
    @Schema(description = "다음 페이지 커서. 더 없으면 null") Long nextCursor,
    @Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {

    public static MediaReportsResponse from(MediaReportPage page) {
        return new MediaReportsResponse(
            page.reports().stream().map(MediaReportSummaryResponse::from).toList(),
            page.nextCursor(),
            page.hasNext()
        );
    }
}
