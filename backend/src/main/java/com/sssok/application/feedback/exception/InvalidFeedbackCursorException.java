package com.sssok.application.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFeedbackCursorException extends SssOkException {

    public InvalidFeedbackCursorException() {
        super(ErrorCode.INVALID_REQUEST_PARAMETER, "cursor");
    }
}
