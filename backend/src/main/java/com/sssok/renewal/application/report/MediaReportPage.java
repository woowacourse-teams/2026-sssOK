package com.sssok.renewal.application.report;

import com.sssok.renewal.domain.report.MediaReport;
import java.util.List;

public record MediaReportPage(List<MediaReport> reports, Long nextCursor, boolean hasNext) {

    public static MediaReportPage of(List<MediaReport> reports, boolean hasNext) {
        Long nextCursor = hasNext ? reports.getLast().getId() : null;
        return new MediaReportPage(reports, nextCursor, hasNext);
    }
}
