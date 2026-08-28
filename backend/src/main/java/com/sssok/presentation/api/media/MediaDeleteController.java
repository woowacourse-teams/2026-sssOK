package com.sssok.presentation.api.media;

import com.sssok.application.media.DeleteMediaResult;
import com.sssok.application.media.DeleteMediaService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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

    @Operation(summary = "미디어 다건 삭제")
    @DeleteMapping
    public ApiResponse<DeleteMediaResponse> deleteAll(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @RequestBody DeleteMediaRequest request
    ) {
        List<Long> mediaIds = request == null ? null : request.mediaIds();
        DeleteMediaResult result = deleteMediaService.deleteAll(roomId, mediaIds, memberId);
        return ApiResponse.of(DeleteMediaResponse.from(result));
    }
}
