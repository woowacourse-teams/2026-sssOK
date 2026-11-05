package com.sssok.renewal.application.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class MediaReportNotFoundException extends SssOkException {
    public MediaReportNotFoundException() {
        super(ErrorCode.MEDIA_REPORT_NOT_FOUND);
    }
}
