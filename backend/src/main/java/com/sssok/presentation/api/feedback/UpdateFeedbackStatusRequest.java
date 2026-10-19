package com.sssok.presentation.api.feedback;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateFeedbackStatusRequest(
    @Schema(description = "UNREFLECTED(미반영)·IN_PROGRESS(처리중)·REFLECTED(반영)·ON_HOLD(보류) 중 하나") String status
) {
}
