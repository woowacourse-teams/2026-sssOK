package com.sssok.domain.feedback;

import com.sssok.domain.feedback.exception.FeedbackTooLongException;
import com.sssok.domain.feedback.exception.InvalidFeedbackContentException;

// 의견 본문 값 객체. 앞뒤 공백을 제거한 뒤 1~1000자여야 한다.
public record FeedbackContent(String value) {

    private static final int MAX_LENGTH = 1000;

    public FeedbackContent {
        if (value == null || value.isBlank()) {
            throw new InvalidFeedbackContentException();
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new FeedbackTooLongException(MAX_LENGTH);
        }
    }
}
