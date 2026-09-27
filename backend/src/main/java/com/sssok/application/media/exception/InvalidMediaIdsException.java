package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaIdsException extends SssOkException {

    public InvalidMediaIdsException() {
        super(ErrorCode.INVALID_PARAM, "미디어 ID 목록이 올바르지 않습니다");
    }
}
