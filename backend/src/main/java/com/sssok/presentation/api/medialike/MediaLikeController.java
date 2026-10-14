package com.sssok.presentation.api.medialike;

import com.sssok.application.medialike.LikeMediaService;
import com.sssok.application.medialike.UnlikeMediaService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 좋아요", description = "방 안 사진에 좋아요를 누르거나 취소한다")
@RestController
@RequestMapping("/rooms/{roomId}/media/{mediaId}/likes")
@RequiredArgsConstructor
public class MediaLikeController {

    private final LikeMediaService likeMediaService;
    private final UnlikeMediaService unlikeMediaService;

    @Operation(
        summary = "좋아요 추가",
        description = "요청한 사람의 좋아요를 남기고 변경 후 상태(liked: true)와 전체 좋아요 수를 돌려준다. "
            + "이미 누른 미디어에 다시 보내도 200이고 개수는 늘지 않는다(멱등). "
            + "없는 미디어·다른 방 미디어·아직 실물이 없는 미디어(RESERVED/FAILED)는 404, "
            + "입장하지 않은 사용자는 403, 만료·삭제된 방은 410이 난다. "
            + "좋아요가 실제로 추가됐을 때만 SSE로 media.likes.updated 이벤트가 발행된다."
    )
    @PutMapping
    public ApiResponse<MediaLikeResponse> like(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "좋아요를 누를 미디어 ID") @PathVariable Long mediaId
    ) {
        return ApiResponse.of(MediaLikeResponse.from(likeMediaService.like(roomId, mediaId, memberId)));
    }

    @Operation(
        summary = "좋아요 취소",
        description = "요청한 사람의 좋아요를 지우고 변경 후 상태(liked: false)와 전체 좋아요 수를 돌려준다. "
            + "누르지 않은 미디어에 보내도 200이다(멱등). 실패 케이스는 좋아요 추가와 같다. "
            + "좋아요가 실제로 취소됐을 때만 SSE로 media.likes.updated 이벤트가 발행된다."
    )
    @DeleteMapping
    public ApiResponse<MediaLikeResponse> unlike(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "좋아요를 취소할 미디어 ID") @PathVariable Long mediaId
    ) {
        return ApiResponse.of(MediaLikeResponse.from(unlikeMediaService.unlike(roomId, mediaId, memberId)));
    }
}
