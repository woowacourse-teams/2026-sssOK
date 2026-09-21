package com.sssok.presentation.api.feedback;

import com.sssok.application.feedback.FeedbackPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FeedbacksResponse(
    @Schema(description = "의견 목록 (최신순)") List<FeedbackSummaryResponse> feedbacks,
    @Schema(description = "다음 페이지 커서. 더 없으면 null") Long nextCursor,
    @Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {

    public static FeedbacksResponse from(FeedbackPage page) {
        return new FeedbacksResponse(
            page.feedbacks().stream().map(FeedbackSummaryResponse::from).toList(),
            page.nextCursor(),
            page.hasNext()
        );
    }
}
