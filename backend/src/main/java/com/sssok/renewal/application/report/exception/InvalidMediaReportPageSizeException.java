package com.sssok.renewal.application.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaReportPageSizeException extends SssOkException {
    public InvalidMediaReportPageSizeException() {
        super(ErrorCode.INVALID_REQUEST_PARAMETER, "size");
    }
}
