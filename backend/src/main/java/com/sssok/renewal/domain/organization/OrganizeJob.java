package com.sssok.renewal.domain.organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizeJob(Long id, UUID publicId, Long roomId, Long requesterId, OrganizeKind kind,
                          OrganizeTrigger trigger, OrganizeJobStatus status, String requestText, String serverId,
                          String toolName, String toolDescription, int photoCount, int excludedCount, int jobVersion,
                          int retryCount, String failureCode, String failureDetail, Integer resultVersion,
                          String result, String progressStage, Integer progressProcessed, Integer progressTotal,
                          String progressUnit, Instant reviewExpiresAt, Instant resultExpiresAt, Instant finishedAt, Instant createdAt,
                          Instant updatedAt) {
    public boolean isOwnedBy(Long memberId) {
        return requesterId.equals(memberId);
    }

    public boolean hasResult() {
        return result != null;
    }

    // 검토 기한이 지났는데 아직 READY 로 남아 있는 작업. 스위퍼가 늦어도 읽고 쓰는 쪽에서 직접 판단한다.
    public boolean isReviewOverdue(Instant now) {
        return status == OrganizeJobStatus.READY && reviewExpiresAt != null && !now.isBefore(reviewExpiresAt);
    }

    public boolean isResultExpired(Instant now) {
        return result == null || (resultExpiresAt != null && !now.isBefore(resultExpiresAt));
    }
}
