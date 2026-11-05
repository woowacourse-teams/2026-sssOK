package com.sssok.renewal.domain.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaReportStatusException extends SssOkException {
    public InvalidMediaReportStatusException(String status) {
        super(ErrorCode.INVALID_MEDIA_REPORT_STATUS, status);
    }
}
