package com.sssok.renewal.application.organization.job;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class OrganizeRateLimitedException extends SssOkException {
    private final long retryAfterSeconds;

    public OrganizeRateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.ORGANIZE_RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
