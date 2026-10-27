package com.sssok.renewal.domain.notification.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidPushSubscriptionException extends SssOkException {
    public InvalidPushSubscriptionException(String reason) {
        super(ErrorCode.INVALID_PUSH_SUBSCRIPTION, reason);
    }
}
