package com.sssok.renewal.domain.notification.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidDeviceTokenException extends SssOkException {
    public InvalidDeviceTokenException(String reason) {
        super(ErrorCode.INVALID_DEVICE_TOKEN, reason);
    }
}
