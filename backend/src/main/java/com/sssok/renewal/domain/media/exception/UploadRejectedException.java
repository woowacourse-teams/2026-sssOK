package com.sssok.renewal.domain.media.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public abstract class UploadRejectedException extends SssOkException {
    public enum Reason { INVALID_PARAM, UNSUPPORTED_MEDIA_TYPE, FILE_TOO_LARGE }
    private final Reason reason;

    protected UploadRejectedException(Reason reason, String message) {
        super(message, switch (reason) {
            case INVALID_PARAM -> ErrorCode.INVALID_PARAM;
            case UNSUPPORTED_MEDIA_TYPE -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case FILE_TOO_LARGE -> ErrorCode.FILE_TOO_LARGE;
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
