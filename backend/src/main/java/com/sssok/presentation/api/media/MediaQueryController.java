package com.sssok.presentation.api.media;

import com.sssok.application.media.GetMediaListService;
import com.sssok.application.media.GetMediaService;
import com.sssok.application.media.GetOriginalUrlService;
import com.sssok.application.media.GetThumbnailUrlService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 조회", description = "방에 올라온 미디어의 메타데이터와, 화면에 그릴 썸네일·원본을 읽는다. 저장 목적의 다운로드는 다운로드 API가 맡는다.")
@RestController
@RequestMapping("/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaQueryController {

    private final GetMediaListService getMediaListService;
    private final GetMediaService getMediaService;
    private final GetThumbnailUrlService getThumbnailUrlService;
    private final GetOriginalUrlService getOriginalUrlService;

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
        description = "미디어 하나의 메타데이터를 반환한다. 목록 항목의 필드를 모두 포함하고 "
            + "여기에 촬영 시각(takenAt)·촬영 위치(location)·삭제 권한(canDelete)이 더해진다. "
            + "takenAt과 location은 원본 EXIF에서 읽으며, 카메라가 남기지 않았거나 위치 기록이 "
            + "꺼져 있었으면 null이다. location.name은 역지오코딩이 붙기 전까지 null이다. "
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

    @Operation(
        summary = "썸네일 표시",
        description = "목록 타일에 그릴 축소본이다. 조회 응답의 thumbnailUrl이 가리키는 주소이며, "
            + "브라우저 img 태그를 위해 이 경로에서만 token 쿼리 파라미터 인증을 허용한다. "
            + "유효기간 5분짜리 서명 URL로 302 리다이렉트한다. inline이라 브라우저가 그대로 표시하므로 "
            + "img 태그에 바로 걸 수 있다. 서명 URL을 조회 응답에 직접 싣지 않고 이 경로를 거치게 한 것은, "
            + "목록을 캐싱해도 URL이 만료되지 않게 하고 방 참여자만 볼 수 있도록 하기 위해서다. "
            + "아직 워커가 만들지 않았거나 영상이라 썸네일이 없으면 404 THUMBNAIL_NOT_FOUND가 난다. "
            + "없는 mediaId나 다른 방의 미디어는 404 MEDIA_NOT_FOUND다."
    )
    @GetMapping("/{mediaId}/thumbnail")
    public ResponseEntity<Void> thumbnail(
        @Parameter(hidden = true) @AuthMember(allowQueryToken = true) Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "썸네일을 볼 미디어 ID") @PathVariable Long mediaId
    ) {
        return redirectTo(getThumbnailUrlService.getUrl(roomId, mediaId));
    }

    @Operation(
        summary = "원본 표시",
        description = "원본을 화면에 크게 띄우기 위한 경로다. 조회 응답의 originalUrl이 가리킨다. "
            + "단건 다운로드(/downloads/media/{mediaId})와 같은 파일을 가리키지만 inline으로 서명해 "
            + "브라우저가 그대로 표시한다 — 저장이 목적이면 다운로드 쪽을 쓴다. "
            + "유효기간 5분짜리 서명 URL로 302 리다이렉트한다. "
            + "없는 mediaId나 다른 방의 미디어는 404, 아직 처리 중이면 409가 난다."
    )
    @GetMapping("/{mediaId}/original")
    public ResponseEntity<Void> original(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "표시할 미디어 ID") @PathVariable Long mediaId
    ) {
        return redirectTo(getOriginalUrlService.getUrl(roomId, mediaId));
    }

    // 서버가 바이트를 프록시하지 않고 스토리지 서명 URL 로 넘긴다.
    private ResponseEntity<Void> redirectTo(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
