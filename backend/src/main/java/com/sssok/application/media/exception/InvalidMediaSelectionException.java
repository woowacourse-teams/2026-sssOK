package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidMediaSelectionException extends SssOkException {

    public InvalidMediaSelectionException() {
        super(ErrorCode.INVALID_PARAM, "미디어 선택 조건이 올바르지 않습니다");
    }
}
