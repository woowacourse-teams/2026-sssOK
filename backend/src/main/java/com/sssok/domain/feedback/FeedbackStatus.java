package com.sssok.domain.feedback;

import com.sssok.domain.feedback.exception.InvalidFeedbackStatusException;
import java.util.Arrays;

// 의견 처리 상태. 관리자가 직접 바꾸며, 순서와 상관없이 어느 상태로든 옮길 수 있다.
public enum FeedbackStatus {

    UNREFLECTED,
    IN_PROGRESS,
    REFLECTED,
    ON_HOLD;

    public static FeedbackStatus from(String name) {
        return Arrays.stream(values())
            .filter(status -> status.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new InvalidFeedbackStatusException(name));
    }
}
