package com.sssok.renewal.application.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaReportCursorException extends SssOkException {
    public InvalidMediaReportCursorException() {
        super(ErrorCode.INVALID_REQUEST_PARAMETER, "cursor");
    }
}
