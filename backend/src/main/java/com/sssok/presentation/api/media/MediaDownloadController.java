package com.sssok.presentation.api.media;

import com.sssok.application.media.GetMediaDownloadUrlService;
import com.sssok.application.media.GetOriginalUrlService;
import com.sssok.application.media.GetThumbnailUrlService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 다운로드", description = "업로드 당시 파일명 그대로 원본을 내려받는다. 서버는 바이트를 프록시하지 않고 스토리지 서명 URL로 리다이렉트한다.")
@RestController
@RequestMapping("/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaDownloadController {

    private final GetMediaDownloadUrlService getMediaDownloadUrlService;
    private final GetThumbnailUrlService getThumbnailUrlService;
    private final GetOriginalUrlService getOriginalUrlService;

    @Operation(
        summary = "단건 다운로드",
        description = "미디어 원본을 업로드 당시 파일명 그대로 내려받는다. 유효기간 5분짜리 스토리지 서명 URL로 302 "
            + "리다이렉트하며, 바디는 없다. 서명 URL의 Content-Disposition에는 ASCII 폴백(filename)과 RFC 5987 "
            + "UTF-8 인코딩(filename*)이 함께 실려 있어 한글 파일명도 깨지지 않는다. 없는 mediaId, 삭제됨, 다른 방의 "
            + "미디어면 404가 나고, 아직 처리 중인 미디어면 409가 난다."
    )
    @GetMapping("/{mediaId}/download")
    public ResponseEntity<Void> download(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "다운로드할 미디어 ID") @PathVariable Long mediaId
    ) {
        String url = getMediaDownloadUrlService.getUrl(roomId, mediaId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @Operation(
        summary = "썸네일 조회",
        description = "목록 타일에 그릴 축소본을 내려받는다. 조회 응답의 thumbnailUrl이 가리키는 주소이며, "
            + "유효기간 5분짜리 서명 URL로 302 리다이렉트한다. 원본과 달리 inline이라 브라우저가 "
            + "그대로 표시한다 — img 태그에 바로 걸 수 있다. 서명 URL을 조회 응답에 직접 싣지 않고 "
            + "이 경로를 거치게 한 것은, 목록을 캐싱해도 URL이 만료되지 않게 하고 방 참여자만 "
            + "볼 수 있도록 하기 위해서다. 아직 워커가 만들지 않았거나 영상이라 썸네일이 없으면 404 "
            + "THUMBNAIL_NOT_FOUND가 난다. 없는 mediaId나 다른 방의 미디어는 404 MEDIA_NOT_FOUND다."
    )
    @GetMapping("/{mediaId}/thumbnail")
    public ResponseEntity<Void> thumbnail(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "썸네일을 볼 미디어 ID") @PathVariable Long mediaId
    ) {
        String url = getThumbnailUrlService.getUrl(roomId, mediaId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @Operation(
        summary = "원본 표시",
        description = "원본을 화면에 크게 띄우기 위한 경로다. 조회 응답의 originalUrl이 가리킨다. "
            + "단건 다운로드와 같은 파일을 가리키지만 inline으로 서명해 브라우저가 그대로 표시한다 — "
            + "저장이 목적이면 /download를 쓴다. 유효기간 5분짜리 서명 URL로 302 리다이렉트한다. "
            + "없는 mediaId나 다른 방의 미디어는 404, 아직 처리 중이면 409가 난다."
    )
    @GetMapping("/{mediaId}/original")
    public ResponseEntity<Void> original(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "표시할 미디어 ID") @PathVariable Long mediaId
    ) {
        String url = getOriginalUrlService.getUrl(roomId, mediaId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
