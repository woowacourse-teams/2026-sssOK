package com.sssok.domain.feedback;

import com.sssok.domain.feedback.exception.FeedbackTooLongException;
import com.sssok.domain.feedback.exception.InvalidFeedbackContentException;

// 의견 본문 값 객체. 앞뒤 공백을 제거한 뒤 1~1000자여야 한다.
public record FeedbackContent(String value) {

    private static final int MAX_LENGTH = 1000;

    // 목록 응답에서 보여줄 미리보기 길이. 전문은 단건 조회에서 본다.
    private static final int PREVIEW_LENGTH = 100;
    private static final String ELLIPSIS = "...";

    public FeedbackContent {
        if (value == null || value.isBlank()) {
            throw new InvalidFeedbackContentException();
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new FeedbackTooLongException(MAX_LENGTH);
        }
    }

    // 목록에서 내려줄 앞부분.
    public String preview() {
        if (value.length() <= PREVIEW_LENGTH) {
            return value;
        }
        return value.substring(0, PREVIEW_LENGTH) + ELLIPSIS;
    }
}
