package com.sssok.domain.file.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.file.UploadStatus;

public class IllegalUploadStatusException extends SssOkException {

    public IllegalUploadStatusException(UploadStatus current, UploadStatus next) {
        super(ErrorCode.ILLEGAL_UPLOAD_STATUS, current, next);
    }
}
