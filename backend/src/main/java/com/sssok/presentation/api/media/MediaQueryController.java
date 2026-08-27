package com.sssok.presentation.api.media;

import com.sssok.application.media.GetMediaListService;
import com.sssok.application.media.GetMediaService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 조회", description = "방에 올라온 미디어의 메타데이터를 읽는다. 실제 바이트는 다운로드 API로 받는다.")
@RestController
@RequestMapping("/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaQueryController {

    private final GetMediaListService getMediaListService;
    private final GetMediaService getMediaService;

    @Operation(
        summary = "미디어 목록 조회",
        description = "방에 올라온 미디어를 최신순으로 내려준다. folderId를 주면 그 폴더에 담긴 것만, "
            + "생략하면 방 전체를 반환한다. 아직 스토리지에 실물이 없는 미디어(발급만 받고 올리지 않았거나 "
            + "업로드에 실패한 것)는 목록에 나오지 않는다. thumbnailUrl·width·duration은 워커가 채우기 "
            + "전까지 null이다. 없는 폴더나 다른 방 폴더로 필터하면 404, 입장하지 않은 사용자는 403, "
            + "없는 방은 404, 만료·삭제된 방은 410이 난다."
    )
    @GetMapping
    public ApiResponse<MediaListResponse> getMediaList(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "이 폴더에 담긴 미디어만 조회한다. 생략하면 방 전체")
        @RequestParam(required = false) Long folderId
    ) {
        return ApiResponse.of(MediaListResponse.from(getMediaListService.list(roomId, folderId)));
    }

    @Operation(
        summary = "미디어 단건 조회",
        description = "미디어 하나의 메타데이터를 반환한다. 응답 형태는 목록의 항목과 같다. "
            + "없는 mediaId, 다른 방의 미디어, 아직 실물이 없는 미디어는 모두 404가 난다 — "
            + "다른 방에 그 ID가 있는지 알 수 없도록 구분하지 않는다. "
            + "방 관련 실패 케이스(403/404/410)는 목록 조회와 동일하다."
    )
    @GetMapping("/{mediaId}")
    public ApiResponse<MediaResponse> getMedia(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "조회할 미디어 ID") @PathVariable Long mediaId
    ) {
        return ApiResponse.of(MediaResponse.from(getMediaService.get(roomId, mediaId)));
    }
}
