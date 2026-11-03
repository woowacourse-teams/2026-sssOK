package com.sssok.renewal.application.report;

public record ReportMediaCommand(Long roomId, Long mediaId, Long memberId, String reason) {
}
