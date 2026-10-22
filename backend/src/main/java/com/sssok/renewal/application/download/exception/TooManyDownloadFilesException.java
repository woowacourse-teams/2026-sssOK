package com.sssok.renewal.application.download.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class TooManyDownloadFilesException extends SssOkException {
    public TooManyDownloadFilesException(int maxCount) {
        super(ErrorCode.TOO_MANY_FILES, maxCount);
    }
}
