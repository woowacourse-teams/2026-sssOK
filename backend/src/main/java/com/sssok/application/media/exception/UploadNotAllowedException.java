package com.sssok.application.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UploadNotAllowedException extends SssOkException {

    public UploadNotAllowedException() {
        super(ErrorCode.UPLOAD_NOT_ALLOWED);
    }
}
