package com.sssok.renewal.application.notification.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class WebPushUnavailableException extends SssOkException {
    public WebPushUnavailableException() {
        super(ErrorCode.WEB_PUSH_UNAVAILABLE);
    }
}
