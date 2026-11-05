package com.sssok.renewal.incoming.report;

import com.sssok.renewal.domain.report.MediaReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record MediaReportSummaryResponse(
    @Schema(description = "신고 ID") Long reportId,
    @Schema(description = "신고된 사진이 있던 방의 ID") Long roomId,
    @Schema(description = "신고 당시 방 이름. 이 값을 저장하기 전에 들어온 신고는 null") String roomName,
    @Schema(description = "신고된 미디어 ID") Long mediaId,
    @Schema(description = "신고한 회원의 ID") Long memberId,
    @Schema(description = "신고 당시 신고자의 방 닉네임. 이 값을 저장하기 전에 들어온 신고는 null") String nickname,
    @Schema(description = "신고 사유. INAPPROPRIATE_CONTENT·PRIVACY_VIOLATION·COPYRIGHT_VIOLATION·HARASSMENT")
    String reason,
    @Schema(description = "처리 상태. UNREFLECTED(미처리)·REFLECTED(처리완료)") String status,
    @Schema(description = "담당자. MAICHAN(마이찬)·HAENI(해니)·YUNDOL(윤돌)·HYUNMIBAP(현미밥). 없으면 null")
    String assignee,
    @Schema(description = "접수 시각") Instant reportedAt
) {

    public static MediaReportSummaryResponse from(MediaReport report) {
        return new MediaReportSummaryResponse(
            report.getId(),
            report.getRoomId(),
            report.getRoomName(),
            report.getMediaId(),
            report.getMemberId(),
            report.getNickname(),
            report.getReason().name(),
            report.getStatus().name(),
            report.getAssignee() == null ? null : report.getAssignee().name(),
            report.getReportedAt()
        );
    }
}
