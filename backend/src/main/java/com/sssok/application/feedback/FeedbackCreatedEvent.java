package com.sssok.application.feedback;

import com.sssok.domain.feedback.Feedback;

// 의견이 저장됐다는 사실. 커밋 뒤 팀 알림을 띄우는 데 쓴다 (FeedbackNotificationTrigger).
public record FeedbackCreatedEvent(Feedback feedback) {
}
