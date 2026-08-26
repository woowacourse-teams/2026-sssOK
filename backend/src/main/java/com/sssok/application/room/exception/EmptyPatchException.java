package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class EmptyPatchException extends SssOkException {

    public EmptyPatchException() {
        super(ErrorCode.EMPTY_PATCH);
    }
}
