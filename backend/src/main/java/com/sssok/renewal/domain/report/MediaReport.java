package com.sssok.renewal.domain.report;

import java.time.Instant;
import lombok.Getter;

// 사진 한 장에 대한 신고 한 건.
@Getter
public class MediaReport {
    private final Long roomId;
    private final Long mediaId;
    private final Long memberId;
    private final MediaReportReason reason;
    private final Instant reportedAt;

    private MediaReport(Long roomId, Long mediaId, Long memberId, MediaReportReason reason, Instant reportedAt) {
        this.roomId = roomId;
        this.mediaId = mediaId;
        this.memberId = memberId;
        this.reason = reason;
        this.reportedAt = reportedAt;
    }

    public static MediaReport create(Long roomId, Long mediaId, Long memberId, MediaReportReason reason,
                                     Instant reportedAt) {
        return new MediaReport(roomId, mediaId, memberId, reason, reportedAt);
    }
}
