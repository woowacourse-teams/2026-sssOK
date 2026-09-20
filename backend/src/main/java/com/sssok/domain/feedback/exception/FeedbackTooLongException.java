package com.sssok.domain.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class FeedbackTooLongException extends SssOkException {

    public FeedbackTooLongException(int maxLength) {
        super(ErrorCode.FEEDBACK_TOO_LONG, maxLength);
    }
}
