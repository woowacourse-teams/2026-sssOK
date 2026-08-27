package com.sssok.presentation.api.media;

import com.sssok.application.media.GetMediaDownloadUrlService;
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
}
