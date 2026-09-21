package com.sssok.application.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFeedbackPageSizeException extends SssOkException {

    public InvalidFeedbackPageSizeException() {
        super(ErrorCode.INVALID_REQUEST_PARAMETER, "size");
    }
}
