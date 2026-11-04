package com.sssok.renewal.incoming.report;

import com.sssok.application.media.MediaUrls;
import com.sssok.renewal.application.report.MediaReportDetail;
import com.sssok.renewal.domain.report.MediaReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record MediaReportResponse(
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
    @Schema(description = "접수 시각") Instant reportedAt,
    @Schema(description = "대상 사진이 갤러리에서 지워졌는지 여부") boolean mediaDeleted,
    @Schema(description = "대상 사진 썸네일 서명 URL. 사진이 지워졌거나 썸네일이 아직 없으면 null")
    String thumbnailUrl,
    @Schema(description = "대상 사진 표시용 서명 URL. 사진이 지워졌으면 null") String displayUrl,
    @Schema(description = "표시용 URL 만료 시각. 사진이 지워졌으면 null") Instant displayUrlExpiresAt,
    @Schema(description = "같은 사진에 들어온 신고 수 (이 신고 포함)") long reportCountOfMedia
) {

    public static MediaReportResponse from(MediaReportDetail detail) {
        MediaReport report = detail.report();
        MediaUrls urls = detail.mediaDeleted() ? MediaUrls.NONE : detail.mediaUrls();
        return new MediaReportResponse(
            report.getId(),
            report.getRoomId(),
            report.getRoomName(),
            report.getMediaId(),
            report.getMemberId(),
            report.getNickname(),
            report.getReason().name(),
            report.getStatus().name(),
            report.getAssignee() == null ? null : report.getAssignee().name(),
            report.getReportedAt(),
            detail.mediaDeleted(),
            urls.thumbnailUrl(),
            urls.displayUrl(),
            urls.displayUrlExpiresAt(),
            detail.reportCountOfMedia()
        );
    }
}
