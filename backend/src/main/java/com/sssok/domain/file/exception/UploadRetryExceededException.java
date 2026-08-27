package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UploadRetryExceededException extends SssOkException {

    public UploadRetryExceededException(int maxRetryCount) {
        super(ErrorCode.UPLOAD_RETRY_EXCEEDED);
    }
}
