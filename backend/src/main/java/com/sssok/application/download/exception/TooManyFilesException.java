package com.sssok.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class TooManyFilesException extends SssOkException {

    public TooManyFilesException(int maxCount) {
        super(ErrorCode.TOO_MANY_FILES, maxCount);
    }
}
