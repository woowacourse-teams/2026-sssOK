package com.sssok.application.feedback;

import com.sssok.domain.feedback.Feedback;
import java.util.List;

// 커서 기반 한 페이지. nextCursor 는 다음 요청에 그대로 넣을 값이고, 더 없으면 null 이다.
public record FeedbackPage(List<Feedback> feedbacks, Long nextCursor, boolean hasNext) {

    public static FeedbackPage of(List<Feedback> feedbacks, boolean hasNext) {
        Long nextCursor = hasNext ? feedbacks.getLast().getId() : null;
        return new FeedbackPage(feedbacks, nextCursor, hasNext);
    }
}
