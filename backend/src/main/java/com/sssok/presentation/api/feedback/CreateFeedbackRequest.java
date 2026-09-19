package com.sssok.presentation.api.feedback;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateFeedbackRequest(
    @Schema(description = "의견 본문. 1~1000자", example = "사진을 100장 올릴 때 진행률이 멈춘 것처럼 보여요.")
    String content
) {
}
