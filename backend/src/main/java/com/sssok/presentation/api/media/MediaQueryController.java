package com.sssok.presentation.api.media;

import com.sssok.application.media.GetMediaListService;
import com.sssok.application.media.GetMediaService;
import com.sssok.application.media.MediaCursor;
import com.sssok.application.media.MediaPage;
import com.sssok.application.media.MediaUploaderFilter;
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

@Tag(name = "미디어 조회", description = "방에 올라온 미디어의 메타데이터를 읽는다. 화면에 그릴 썸네일·원본은 "
    + "응답의 thumbnailUrl·originalUrl(R2 서명 URL)을 바로 쓴다. 저장 목적의 다운로드는 다운로드 API가 맡는다.")
@RestController
@RequestMapping("/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaQueryController {

    private final GetMediaListService getMediaListService;
    private final GetMediaService getMediaService;
    private final MediaCursorCodec mediaCursorCodec;

    @Operation(
        summary = "미디어 목록 조회",
        description = "방에 올라온 미디어를 createdAt·mediaId 기준 최신순으로 페이지 조회한다. "
            + "첫 요청은 cursor를 생략하고, 다음 요청부터 직전 응답의 nextCursor를 그대로 전달한다. "
            + "size는 기본 30개, 최대 100개다. 응답의 hasNext는 다음 페이지 존재 여부이며, "
            + "마지막 페이지의 nextCursor는 null이다. totalCount는 각 요청 시점에 조회 조건을 만족하는 "
            + "실시간 전체 개수라 페이지를 탐색하는 동안 달라질 수 있다. "
            + "folderId를 주면 그 폴더에 담긴 것만, 생략하면 방 전체를 조회한다. "
            + "uploader는 ALL(전체)·ME(내가 올린 미디어)·OTHERS(다른 사람이 올린 미디어)를 "
            + "지원하며, 생략하면 ALL이다. "
            + "아직 스토리지에 실물이 없는 미디어(발급만 받고 올리지 않았거나 "
            + "업로드에 실패한 것)는 목록에 나오지 않는다. thumbnailUrl·width는 워커가 채우기 전까지 "
            + "null이고, duration은 영상에만 값이 있다. "
            + "thumbnailUrl·originalUrl은 R2 서명 URL이라 각각 만료 시각(thumbnailUrlExpiresAt·"
            + "originalUrlExpiresAt)이 지나면 깨지므로, 지난 뒤에는 목록을 다시 받아야 한다. "
            + "없는 폴더나 다른 방 폴더로 필터하면 404, 입장하지 않은 사용자는 403, "
            + "없는 방은 404, 만료·삭제된 방은 410이 난다."
    )
    @GetMapping
    public ApiResponse<MediaListResponse> getMediaList(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "이 폴더에 담긴 미디어만 조회한다. 생략하면 방 전체")
        @RequestParam(required = false) Long folderId,
        @Parameter(description = "업로더 필터. ALL(전체), ME(내 미디어), OTHERS(다른 사람 미디어)")
        @RequestParam(defaultValue = "ALL") MediaUploaderFilter uploader,
        @Parameter(description = "직전 응답의 nextCursor. 첫 페이지는 생략")
        @RequestParam(required = false) String cursor,
        @Parameter(description = "페이지 크기. 기본 30, 최대 100")
        @RequestParam(defaultValue = "30") int size
    ) {
        MediaCursor decodedCursor = cursor == null
            ? null
            : mediaCursorCodec.decode(cursor, roomId, folderId, memberId, uploader);
        MediaPage page = getMediaListService.page(
            roomId, folderId, memberId, uploader, size, decodedCursor);
        String nextCursor = page.nextCursor() == null
            ? null
            : mediaCursorCodec.encode(page.nextCursor());
        return ApiResponse.of(MediaListResponse.from(page, nextCursor));
    }

    @Operation(
        summary = "미디어 전체 목록 조회",
        description = "페이지네이션 없이 방에 올라온 미디어 전체를 createdAt·mediaId 기준 최신순으로 "
            + "내려준다. folderId를 주면 그 폴더에 담긴 것만, 생략하면 방 전체를 반환한다. "
            + "응답 크기와 서버 메모리 사용량이 미디어 수에 비례하므로 전체 목록이 반드시 필요한 "
            + "기능에서만 사용한다. 일부 목록만 필요한 화면은 커서 페이지네이션 API를 사용한다. "
            + "아직 스토리지에 실물이 없는 미디어는 목록에 나오지 않는다. "
            + "없는 폴더나 다른 방 폴더로 필터하면 404, 입장하지 않은 사용자는 403, "
            + "없는 방은 404, 만료·삭제된 방은 410이 난다."
    )
    @GetMapping("/all")
    public ApiResponse<AllMediaListResponse> getAllMedia(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "이 폴더에 담긴 미디어만 조회한다. 생략하면 방 전체")
        @RequestParam(required = false) Long folderId
    ) {
        return ApiResponse.of(AllMediaListResponse.from(
            getMediaListService.list(roomId, folderId)));
    }

    @Operation(
        summary = "미디어 단건 조회",
        description = "미디어 하나의 메타데이터를 반환한다. 목록 항목의 필드를 모두 포함하고 "
            + "여기에 촬영 시각(takenAt)·촬영 위치(location)·삭제 권한(canDelete)이 더해진다. "
            + "takenAt과 location은 사진이면 원본 EXIF에서, 영상이면 컨테이너 메타데이터에서 읽으며, "
            + "기기가 남기지 않았거나 위치 기록이 꺼져 있었으면 null이다. "
            + "location.name은 역지오코딩이 붙기 전까지 null이다. "
            + "canDelete는 보는 사람에 따라 달라져서 목록에는 싣지 않는다 — 올린 본인과 방장만 true다. "
            + "없는 mediaId, 다른 방의 미디어, 아직 실물이 없는 미디어는 모두 404가 난다 — "
            + "다른 방에 그 ID가 있는지 알 수 없도록 구분하지 않는다. "
            + "방 관련 실패 케이스(403/404/410)는 목록 조회와 동일하다."
    )
    @GetMapping("/{mediaId}")
    public ApiResponse<MediaFullResponse> getMedia(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "조회할 미디어 ID") @PathVariable Long mediaId
    ) {
        return ApiResponse.of(MediaFullResponse.from(getMediaService.get(roomId, mediaId, memberId)));
    }
}
