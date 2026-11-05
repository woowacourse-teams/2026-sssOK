package com.sssok.renewal.domain.report;

import com.sssok.domain.feedback.FeedbackAssignee;
import java.time.Instant;
import lombok.Getter;

// 담당자는 의견과 같은 팀원 목록을 쓴다.
// 닉네임·방 이름은 나중에 바뀌거나 방이 사라져도 남도록 신고 당시 값을 복사해 둔다.
@Getter
public class MediaReport {
    private final Long id;
    private final Long roomId;
    private final String roomName;
    private final Long mediaId;
    private final Long memberId;
    private final String nickname;
    private final MediaReportReason reason;
    private final Instant reportedAt;
    private MediaReportStatus status;
    private FeedbackAssignee assignee;

    private MediaReport(Long id, Long roomId, String roomName, Long mediaId, Long memberId, String nickname,
                        MediaReportReason reason, Instant reportedAt, MediaReportStatus status,
                        FeedbackAssignee assignee) {
        this.id = id;
        this.roomId = roomId;
        this.roomName = roomName;
        this.mediaId = mediaId;
        this.memberId = memberId;
        this.nickname = nickname;
        this.reason = reason;
        this.reportedAt = reportedAt;
        this.status = status;
        this.assignee = assignee;
    }

    public static MediaReport create(Long roomId, String roomName, Long mediaId, Long memberId, String nickname,
                                     MediaReportReason reason, Instant reportedAt) {
        return new MediaReport(null, roomId, roomName, mediaId, memberId, nickname, reason, reportedAt,
            MediaReportStatus.UNREFLECTED, null);
    }

    public static MediaReport reconstruct(Long id, Long roomId, String roomName, Long mediaId, Long memberId,
                                          String nickname, MediaReportReason reason, Instant reportedAt,
                                          MediaReportStatus status, FeedbackAssignee assignee) {
        return new MediaReport(id, roomId, roomName, mediaId, memberId, nickname, reason, reportedAt, status,
            assignee);
    }

    public MediaReport withId(Long id) {
        return new MediaReport(id, roomId, roomName, mediaId, memberId, nickname, reason, reportedAt, status,
            assignee);
    }

    public void changeAssignee(FeedbackAssignee assignee) {
        this.assignee = assignee;
    }

    public void changeStatus(MediaReportStatus status) {
        this.status = status;
    }
}
