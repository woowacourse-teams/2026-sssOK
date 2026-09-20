package com.sssok.presentation.api.feedback;

import com.sssok.domain.feedback.Feedback;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CreateFeedbackResponse(
    @Schema(description = "저장된 의견의 ID") Long feedbackId,
    @Schema(description = "저장 시각") Instant createdAt
) {

    public static CreateFeedbackResponse from(Feedback feedback) {
        return new CreateFeedbackResponse(feedback.getId(), feedback.getCreatedAt());
    }
}
