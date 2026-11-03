package com.sssok.renewal.application.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaAlreadyReportedException extends SssOkException {
    public MediaAlreadyReportedException() {
        super(ErrorCode.MEDIA_ALREADY_REPORTED);
    }
}
