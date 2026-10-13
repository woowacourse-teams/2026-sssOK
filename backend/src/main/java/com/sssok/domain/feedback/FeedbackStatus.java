package com.sssok.domain.feedback;

import com.sssok.domain.feedback.exception.InvalidFeedbackStatusException;
import java.util.Arrays;

// 의견이 서비스에 반영됐는지. 관리자가 직접 바꾸며, 반영했다가 되돌리는 것도 허용한다.
public enum FeedbackStatus {

    UNREFLECTED,
    REFLECTED;

    public static FeedbackStatus from(String name) {
        return Arrays.stream(values())
            .filter(status -> status.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new InvalidFeedbackStatusException(name));
    }
}
