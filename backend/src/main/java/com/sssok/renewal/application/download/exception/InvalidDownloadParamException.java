package com.sssok.renewal.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidDownloadParamException extends SssOkException {
    private InvalidDownloadParamException(String message) {
        super(message, ErrorCode.INVALID_PARAM);
    }

    public static InvalidDownloadParamException invalidTarget() {
        return new InvalidDownloadParamException("다운로드 조건이 올바르지 않습니다");
    }

    public static InvalidDownloadParamException invalidMediaIds() {
        return new InvalidDownloadParamException("미디어 ID 목록이 올바르지 않습니다");
    }
}
