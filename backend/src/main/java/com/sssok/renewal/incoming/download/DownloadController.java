package com.sssok.renewal.incoming.download;

import com.sssok.renewal.application.download.batch.BatchDownloadFile;
import com.sssok.renewal.application.download.batch.CreateBatchDownloadService;
import com.sssok.renewal.application.download.single.GetMediaDownloadUrlService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.renewal.incoming.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 다운로드는 단건(리다이렉트)·다건(서명 URL 목록)·zip(비동기 압축 잡) 세 방식이 /downloads 아래 하위 경로로 갈린다.
// 이 컨트롤러는 단건·다건을 맡는다. zip 은 아직 presentation 의 ZipDownloadController 에 있다(#582).
// renewal 패키지는 /api/v1 자동 접두사(WebConfig) 대상이 아니라 경로에 직접 적는다.
@Tag(name = "다운로드", description = "미디어를 내려받는 세 가지 방식 — 단건 리다이렉트, 압축 없이 서명 URL만 받는 "
    + "다건, zip으로 압축하는 비동기 작업.")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final GetMediaDownloadUrlService getMediaDownloadUrlService;
    private final CreateBatchDownloadService createBatchDownloadService;

    @Operation(
        summary = "단건 다운로드",
        description = "미디어 원본을 업로드 당시 파일명 그대로 내려받는다. 유효기간 5분짜리 스토리지 서명 URL로 302 "
            + "리다이렉트하며, 바디는 없다. 서명 URL의 Content-Disposition에는 ASCII 폴백(filename)과 RFC 5987 "
            + "UTF-8 인코딩(filename*)이 함께 실려 있어 한글 파일명도 깨지지 않는다. 없는 mediaId, 삭제됨, 다른 방의 "
            + "미디어면 404가 난다. 원본 업로드가 끝난 PROCESSING·READY 미디어는 다운로드할 수 있고, 실물이 없는 "
            + "RESERVED·FAILED 미디어는 404가 난다."
    )
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Void> downloadMedia(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "다운로드할 미디어 ID") @PathVariable Long mediaId
    ) {
        String url = getMediaDownloadUrlService.getUrl(roomId, mediaId);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @Operation(
        summary = "다건 다운로드 URL 발급",
        description = "선택한 미디어들을 압축 없이 파일마다 서명 다운로드 URL로 즉시 받는다. "
            + "mediaIds로 대상을 직접 지정하거나 folderId로 폴더 전체를 지정한다. 둘을 동시에 보내거나 "
            + "둘 다 생략하면 400이다. 해석된 실제 대상이 1000개를 "
            + "초과하면 400이 난다. 다른 방 또는 존재하지 않는 ID는 대상에서 제외한다. "
            + "원본 업로드가 끝난 PROCESSING·READY 미디어는 대상에 포함하고, 실물이 없는 RESERVED·FAILED 미디어는 "
            + "제외한다. 그 결과 대상이 하나도 없으면 404가 난다. URL 유효기간은 단건 다운로드와 같다(기본 5분). "
            + "uploader는 ALL(전체)·ME(내가 올린 것)·OTHERS(남이 올린 것)로 대상을 좁히며 "
            + "생략하면 ALL, 지원하지 않는 값은 400이다. "
            + "uploader는 folderId 방식에서만 사용할 수 있으며 mediaIds와 함께 보내면 400이다. "
            + "예: {\"folderId\":31,\"uploader\":\"ME\"} 는 31번 폴더에서 내가 올린 미디어만 받는다."
    )
    @PostMapping("/batch")
    public ApiResponse<CreateBatchDownloadResponse> createBatch(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody CreateBatchDownloadRequest request
    ) {
        List<BatchDownloadFile> files =
            createBatchDownloadService.create(request.toCommand(roomId, memberId));
        return ApiResponse.of(CreateBatchDownloadResponse.from(files));
    }
}
