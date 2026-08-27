package com.sssok.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class DownloadRateLimitedException extends SssOkException {

    public DownloadRateLimitedException() {
        super(ErrorCode.RATE_LIMITED);
    }
}
