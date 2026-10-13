package com.sssok.domain.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFeedbackStatusException extends SssOkException {

    public InvalidFeedbackStatusException(String status) {
        super(ErrorCode.INVALID_FEEDBACK_STATUS, status);
    }
}
