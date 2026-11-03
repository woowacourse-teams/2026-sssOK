package com.sssok.renewal.domain.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaReportReasonException extends SssOkException {
    public InvalidMediaReportReasonException(String reason) {
        super(ErrorCode.INVALID_MEDIA_REPORT_REASON, reason);
    }
}
