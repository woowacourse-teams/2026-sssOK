package com.sssok.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidDownloadParamException extends SssOkException {

    public InvalidDownloadParamException() {
        super(ErrorCode.INVALID_PARAM, "다운로드 조건이 올바르지 않습니다");
    }
}
