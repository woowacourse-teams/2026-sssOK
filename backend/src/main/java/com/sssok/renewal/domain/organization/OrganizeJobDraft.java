package com.sssok.renewal.domain.organization;

import java.util.UUID;

// 저장 직전의 새 작업. 상태는 항상 QUEUED 로 시작한다.
public record OrganizeJobDraft(UUID publicId, Long roomId, Long requesterId, OrganizeKind kind,
                               OrganizeTrigger trigger, String requestText, String serverId, String toolName,
                               String toolDescription, int photoCount, int excludedCount) {
}
