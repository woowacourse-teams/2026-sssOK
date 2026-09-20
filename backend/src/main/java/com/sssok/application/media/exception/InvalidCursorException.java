package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidCursorException extends SssOkException {

    public InvalidCursorException() {
        super(ErrorCode.INVALID_CURSOR);
    }
}
