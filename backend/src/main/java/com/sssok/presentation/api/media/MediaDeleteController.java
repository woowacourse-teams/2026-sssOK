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
        description = "selection으로 고른 미디어를 삭제한다. "
            + "folderId를 주면 그 폴더에 담긴 것으로 대상을 좁히고, 생략하면 방 전체에서 고른다. "
            + "uploader는 ALL(전체)·ME(내가 올린 것)·OTHERS(남이 올린 것)로 업로더를 좁히며 "
            + "생략하면 ALL, 지원하지 않는 값은 400이다. "
            + "셋은 함께 쓸 수 있고 selection → folderId → uploader 순서로 좁혀진다. "
            + "업로더 조건에 걸러진 미디어는 notFoundMediaIds에 들어가지 않는다 — 지우지 않았을 뿐 존재하는 미디어다. "
            + "예: {\"selection\":{\"mode\":\"exclude\",\"ids\":[]},\"folderId\":31,\"uploader\":\"ME\"} 는 "
            + "31번 폴더에서 내가 올린 미디어를 전부 지운다."
    )
    @DeleteMapping
    public ApiResponse<DeleteMediaResponse> deleteAll(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @RequestBody DeleteMediaRequest request
    ) {
        DeleteMediaResult result = deleteMediaService.deleteAll(
            roomId,
            request == null || request.selection() == null ? null : request.selection().toSelection(),
            request == null ? null : request.folderId(),
            memberId,
            request == null ? null : request.uploader());
        return ApiResponse.of(DeleteMediaResponse.from(result));
    }
}
