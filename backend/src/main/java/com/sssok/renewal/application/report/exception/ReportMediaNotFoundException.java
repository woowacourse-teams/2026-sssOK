package com.sssok.renewal.application.report.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

// 없는 미디어·다른 방 미디어·갤러리에 보이지 않는 RESERVED·FAILED 미디어를 같은 404 로 응답한다.
public class ReportMediaNotFoundException extends SssOkException {
    public ReportMediaNotFoundException() {
        super(ErrorCode.MEDIA_NOT_FOUND);
    }
}
