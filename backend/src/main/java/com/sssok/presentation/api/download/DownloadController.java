package com.sssok.presentation.api.download;

import com.sssok.application.download.CreateDownloadJobResult;
import com.sssok.application.download.CreateDownloadJobService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
