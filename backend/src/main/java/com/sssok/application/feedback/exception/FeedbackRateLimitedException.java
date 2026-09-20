package com.sssok.application.feedback.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import lombok.Getter;

// 연속 등록 제한. 언제 다시 시도할 수 있는지를 함께 들고 있다가 Retry-After 헤더로 나간다
@Getter
public class FeedbackRateLimitedException extends SssOkException {

    private final long retryAfterSeconds;

    public FeedbackRateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.FEEDBACK_RATE_LIMITED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
