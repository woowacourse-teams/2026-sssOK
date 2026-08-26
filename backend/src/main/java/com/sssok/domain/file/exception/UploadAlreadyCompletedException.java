package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UploadAlreadyCompletedException extends SssOkException {

    public UploadAlreadyCompletedException() {
        super(ErrorCode.UPLOAD_ALREADY_COMPLETED);
    }
}
