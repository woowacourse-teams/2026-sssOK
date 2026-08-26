package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidUploadParamException extends SssOkException {

    public InvalidUploadParamException(String message) {
        super(ErrorCode.INVALID_PARAM, message);
    }
}
