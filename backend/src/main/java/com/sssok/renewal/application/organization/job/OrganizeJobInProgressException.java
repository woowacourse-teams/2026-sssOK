package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.renewal.domain.organization.OrganizeJob;
import java.util.UUID;

// 진행 중인 작업이 요청자 본인의 것일 때만 ID 를 알려 준다. 다른 멤버의 작업은 존재 여부만 드러낸다.
public class OrganizeJobInProgressException extends SssOkException {
    private final transient UUID activeJobId;

    public OrganizeJobInProgressException(UUID activeJobId) {
        super(ErrorCode.ORGANIZE_JOB_IN_PROGRESS);
        this.activeJobId = activeJobId;
    }

    public static OrganizeJobInProgressException of(OrganizeJob active, Long requesterId) {
        return new OrganizeJobInProgressException(active.isOwnedBy(requesterId) ? active.publicId() : null);
    }

    public UUID activeJobId() {
        return activeJobId;
    }
}
