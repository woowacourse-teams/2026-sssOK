package com.sssok.presentation.api.feedback;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateFeedbackStatusRequest(
    @Schema(description = "UNREFLECTED(미반영) 또는 REFLECTED(반영)") String status
) {
}
