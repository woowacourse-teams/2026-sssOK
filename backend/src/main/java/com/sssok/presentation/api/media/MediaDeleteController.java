package com.sssok.presentation.api.media;

import com.sssok.application.media.DeleteMediaResult;
import com.sssok.application.media.DeleteMediaService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 삭제", description = "본인이 올린 미디어를 삭제하며 방장은 방의 모든 미디어를 삭제할 수 있다.")
@RestController
@RequestMapping("/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaDeleteController {

    private final DeleteMediaService deleteMediaService;

    @Operation(summary = "미디어 단건 삭제")
    @DeleteMapping("/{mediaId}")
    public ApiResponse<DeleteSingleMediaResponse> deleteOne(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @PathVariable Long mediaId
    ) {
        Long deletedId = deleteMediaService.deleteOne(roomId, mediaId, memberId);
        return ApiResponse.of(new DeleteSingleMediaResponse(deletedId));
    }

    @Operation(
        summary = "미디어 다건 삭제",
        description = "mediaIds로 지정한 미디어를 삭제한다. 중복 ID는 한 번만 처리하고, "
            + "존재하지 않거나 다른 방의 ID는 notFoundMediaIds로 알려준다. 빈 배열은 삭제 0건으로 "
            + "성공한다. 요청자가 올린 미디어만 삭제할 수 있고 방장은 방의 모든 미디어를 삭제할 수 있다. "
            + "실제 삭제 대상이 500개를 초과하거나 mediaIds가 없거나 잘못된 값이면 400이다."
    )
    @DeleteMapping
    public ApiResponse<DeleteMediaResponse> deleteAll(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @RequestBody DeleteMediaRequest request
    ) {
        DeleteMediaResult result = deleteMediaService.deleteAll(
            roomId,
            request == null ? null : request.mediaIds(),
            memberId);
        return ApiResponse.of(DeleteMediaResponse.from(result));
    }
}
