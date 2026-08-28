package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaDeleteParamException extends SssOkException {

    public InvalidMediaDeleteParamException() {
        super(ErrorCode.INVALID_PARAM, "삭제할 미디어가 없습니다");
    }
}
