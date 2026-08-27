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

@Tag(name = "다운로드", description = "선택한 미디어를 zip으로 압축해 내려받는 비동기 작업")
@RestController
@RequestMapping("/rooms/{roomId}/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final CreateDownloadJobService createDownloadJobService;
    private final GetDownloadJobStatusService getDownloadJobStatusService;

    @Operation(
        summary = "zip 다운로드 요청",
        description = "선택한 미디어들을 하나의 zip으로 압축하는 작업을 생성한다. 즉시 완료되지 않고 jobId를 돌려준다. "
            + "mediaIds와 folderId를 동시에 보내면 400, mediaIds가 1000개를 초과하면 400이 난다. "
            + "둘 다 생략하면 방 전체 미디어가 대상이다. 처리 중인 미디어는 압축 대상과 mediaCount에서 제외되며, "
            + "그 결과 대상이 하나도 없으면 404가 난다. 동시 진행 중인 압축 잡 수가 많으면 429가 난다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CreateDownloadJobResponse> create(
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
    @GetMapping("/{jobId}")
    public ApiResponse<GetDownloadJobStatusResponse> status(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @Parameter(description = "B-7-1에서 받은 압축 작업 ID") @PathVariable Long jobId
    ) {
        GetDownloadJobStatusResult result = getDownloadJobStatusService.getStatus(jobId, memberId);
        return ApiResponse.of(GetDownloadJobStatusResponse.from(result));
    }
}
