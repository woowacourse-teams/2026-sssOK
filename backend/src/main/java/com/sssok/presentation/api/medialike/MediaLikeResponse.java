package com.sssok.presentation.api.medialike;

import com.sssok.application.medialike.MediaLikeResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record MediaLikeResponse(
    Long mediaId,
    @Schema(description = "요청한 사람이 지금 이 미디어에 좋아요를 누른 상태인지") boolean liked,
    @Schema(description = "이 미디어의 전체 좋아요 수") long likeCount
) {
    public static MediaLikeResponse from(MediaLikeResult result) {
        return new MediaLikeResponse(result.mediaId(), result.liked(), result.likeCount());
    }
}
