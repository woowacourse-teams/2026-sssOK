package com.sssok.renewal.application.notification.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class UploadRequestCooldownException extends SssOkException {
    public UploadRequestCooldownException() {
        super(ErrorCode.UPLOAD_REQUEST_COOLDOWN);
    }
}
