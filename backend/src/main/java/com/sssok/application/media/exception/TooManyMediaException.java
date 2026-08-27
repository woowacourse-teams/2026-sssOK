package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class TooManyMediaException extends SssOkException {

    public TooManyMediaException(int maxCount) {
        super(ErrorCode.TOO_MANY_FILES, maxCount);
    }
}
