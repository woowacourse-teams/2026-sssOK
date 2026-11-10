package com.sssok.renewal.application.media.upload.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UploadVerificationUnavailableException extends SssOkException {
    public UploadVerificationUnavailableException() {
        super(ErrorCode.UPLOAD_VERIFICATION_UNAVAILABLE);
    }
}
