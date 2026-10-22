package com.sssok.presentation.api.download;

import com.sssok.application.download.CreateDownloadJobResult;
import com.sssok.application.download.CreateDownloadJobService;
import com.sssok.application.download.GetDownloadJobStatusResult;
import com.sssok.application.download.GetDownloadJobStatusService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// zip 압축 다운로드(요청·상태 조회)만 남긴 기존 구조의 컨트롤러다.
// 단건·다건은 renewal/incoming/download/DownloadController 로 옮겼고, zip 도 #582 에서 옮긴다.
@Tag(name = "다운로드")
@RestController
@RequestMapping("/rooms/{roomId}/downloads")
@RequiredArgsConstructor
public class ZipDownloadController {

    private final CreateDownloadJobService createDownloadJobService;
    private final GetDownloadJobStatusService getDownloadJobStatusService;

    @Operation(
        summary = "zip 다운로드 요청",
        description = "선택한 미디어들을 하나의 zip으로 압축하는 작업을 생성한다. 즉시 완료되지 않고 jobId를 돌려준다. "
            + "mediaIds로 대상을 직접 지정하거나 folderId로 폴더 전체를 지정한다. 둘을 동시에 보내거나 "
            + "둘 다 생략하면 400이며, "
            + "해석된 실제 대상이 1000개를 초과하면 400이 난다. 원본 업로드가 끝난 PROCESSING·READY 미디어는 "
            + "압축 대상과 mediaCount에 포함하고, 실물이 없는 RESERVED·FAILED 미디어는 제외한다. 그 결과 대상이 하나도 "
            + "없으면 404가 난다. 동시 진행 중인 압축 잡 수가 많으면 429가 난다. "
            + "uploader는 ALL(전체)·ME(내가 올린 것)·OTHERS(남이 올린 것)로 대상을 좁히며 "
            + "생략하면 ALL, 지원하지 않는 값은 400이다. "
            + "uploader는 folderId 방식에서만 사용할 수 있으며 mediaIds와 함께 보내면 400이다. "
            + "mediaCount는 uploader까지 적용한 최종 대상 수이며, 실제 압축 대상과 항상 같다. "
            + "예: {\"folderId\":31,\"uploader\":\"ME\"} 는 31번 폴더에서 내가 올린 미디어만 압축한다."
    )
    @PostMapping("/zip")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CreateDownloadJobResponse> createZip(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody CreateDownloadJobRequest request
    ) {
        CreateDownloadJobResult result =
            createDownloadJobService.create(roomId, memberId,
                request == null ? null : request.mediaIds(),
                request == null ? null : request.folderId(),
                request == null ? null : request.uploader());
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
