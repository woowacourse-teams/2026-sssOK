package com.sssok.domain.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidFeedbackAssigneeException extends SssOkException {

    public InvalidFeedbackAssigneeException(String assignee) {
        super(ErrorCode.INVALID_FEEDBACK_ASSIGNEE, assignee);
    }
}
