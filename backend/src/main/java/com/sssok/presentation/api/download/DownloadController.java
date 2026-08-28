package com.sssok.presentation.api.download;

import com.sssok.application.download.BatchDownloadFile;
import com.sssok.application.download.CreateBatchDownloadService;
import com.sssok.application.download.CreateDownloadJobResult;
import com.sssok.application.download.CreateDownloadJobService;
import com.sssok.application.download.GetDownloadJobStatusResult;
import com.sssok.application.download.GetDownloadJobStatusService;
import com.sssok.application.media.GetMediaDownloadUrlService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 미디어 다운로드 관련 엔드포인트를 전부 여기 하나로 모은다 — 단건(리다이렉트) · 다건(서명 URL 목록) ·
// zip(비동기 압축 잡) 세 가지 방식 모두 이 컨트롤러 밑에서 하위 경로로만 갈린다
// (/downloads/media, /downloads/batch, /downloads/zip). 예전엔 단건만 MediaDownloadController로
// 따로 있었는데, 다건이 추가되며 세 방식의 URL 체계를 통일하기 위해 합쳤다.
@Tag(name = "다운로드", description = "미디어를 내려받는 세 가지 방식 — 단건 리다이렉트, 압축 없이 서명 URL만 받는 "
    + "다건, zip으로 압축하는 비동기 작업.")
@RestController
@RequestMapping("/rooms/{roomId}/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final GetMediaDownloadUrlService getMediaDownloadUrlService;
    private final CreateBatchDownloadService createBatchDownloadService;
    private final CreateDownloadJobService createDownloadJobService;
    private final GetDownloadJobStatusService getDownloadJobStatusService;

    @Operation(
        summary = "단건 다운로드",
        description = "미디어 원본을 업로드 당시 파일명 그대로 내려받는다. 유효기간 5분짜리 스토리지 서명 URL로 302 "
            + "리다이렉트하며, 바디는 없다. 서명 URL의 Content-Disposition에는 ASCII 폴백(filename)과 RFC 5987 "
            + "UTF-8 인코딩(filename*)이 함께 실려 있어 한글 파일명도 깨지지 않는다. 없는 mediaId, 삭제됨, 다른 방의 "
            + "미디어면 404가 나고, 아직 처리 중인 미디어면 409가 난다."
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
        description = "선택한 미디어들을 압축 없이 파일마다 서명 다운로드 URL로 즉시 받는다. mediaIds와 folderId를 "
            + "동시에 보내면 400, mediaIds가 1000개를 초과하면 400이 난다. 둘 다 생략하면 방 전체 미디어가 대상이다. "
            + "처리 중인 미디어는 대상에서 제외되며, 그 결과 대상이 하나도 없으면 404가 난다. URL 유효기간은 단건 "
            + "다운로드와 같다(기본 5분)."
    )
    @PostMapping("/batch")
    public ApiResponse<CreateBatchDownloadResponse> createBatch(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody CreateBatchDownloadRequest request
    ) {
        List<BatchDownloadFile> files =
            createBatchDownloadService.create(roomId, request.mediaIds(), request.folderId());
        return ApiResponse.of(CreateBatchDownloadResponse.from(files));
    }

    @Operation(
        summary = "zip 다운로드 요청",
        description = "선택한 미디어들을 하나의 zip으로 압축하는 작업을 생성한다. 즉시 완료되지 않고 jobId를 돌려준다. "
            + "mediaIds와 folderId를 동시에 보내면 400, mediaIds가 1000개를 초과하면 400이 난다. "
            + "둘 다 생략하면 방 전체 미디어가 대상이다. 처리 중인 미디어는 압축 대상과 mediaCount에서 제외되며, "
            + "그 결과 대상이 하나도 없으면 404가 난다. 동시 진행 중인 압축 잡 수가 많으면 429가 난다."
    )
    @PostMapping("/zip")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CreateDownloadJobResponse> createZip(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody CreateDownloadJobRequest request
    ) {
        CreateDownloadJobResult result =
            createDownloadJobService.create(roomId, memberId, request.mediaIds(), request.folderId());
        return ApiResponse.of(CreateDownloadJobResponse.from(result));
    }

    @Operation(
        summary = "zip 다운로드 상태 조회",
        description = "압축 작업의 진행 상태를 조회하고, 완료되면 다운로드 URL을 받는다. 본인이 요청한 잡만 조회할 수 "
            + "있고, 아니면 403이 난다. 없는 jobId는 404, 보관 기간(기본 1시간, READY 시점부터 계산)이 지났으면 "
            + "410이 난다. downloadUrl/expiresAt은 READY일 때만 채워지며, 조회할 때마다 새로 서명한다."
    )
    @GetMapping("/zip/{jobId}")
    public ApiResponse<GetDownloadJobStatusResponse> zipStatus(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "zip 다운로드 요청에서 받은 압축 작업 ID") @PathVariable Long jobId
    ) {
        GetDownloadJobStatusResult result = getDownloadJobStatusService.getStatus(jobId, memberId);
        return ApiResponse.of(GetDownloadJobStatusResponse.from(result));
    }
}
