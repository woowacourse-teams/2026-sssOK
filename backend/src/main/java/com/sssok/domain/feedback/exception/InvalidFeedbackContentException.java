package com.sssok.domain.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFeedbackContentException extends SssOkException {

    public InvalidFeedbackContentException() {
        super(ErrorCode.INVALID_FEEDBACK_CONTENT);
    }
}
