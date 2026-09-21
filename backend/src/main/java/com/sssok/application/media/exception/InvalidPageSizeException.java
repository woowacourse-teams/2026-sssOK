package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidPageSizeException extends SssOkException {

    public InvalidPageSizeException() {
        super(ErrorCode.INVALID_PAGE_SIZE);
    }
}
