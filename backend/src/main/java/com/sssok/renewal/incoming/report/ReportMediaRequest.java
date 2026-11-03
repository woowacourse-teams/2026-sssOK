package com.sssok.renewal.incoming.report;

import com.sssok.renewal.application.report.ReportMediaCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReportMediaRequest(
    @Schema(description = "신고 사유",
        allowableValues = {"INAPPROPRIATE_CONTENT", "PRIVACY_VIOLATION", "COPYRIGHT_VIOLATION", "HARASSMENT"})
    @NotBlank(message = "reason 값을 보내주세요") String reason
) {
    public ReportMediaCommand toCommand(Long roomId, Long mediaId, Long memberId) {
        return new ReportMediaCommand(roomId, mediaId, memberId, reason);
    }
}
