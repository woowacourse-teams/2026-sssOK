package com.sssok.presentation.api.media;

import com.sssok.application.media.CompleteUploadResult;
import com.sssok.application.media.CompleteUploadService;
import com.sssok.application.media.IssueUploadUrlsResult;
import com.sssok.application.media.IssueUploadUrlsService;
import com.sssok.application.media.UploadFileCommand;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 업로드", description = "서명 URL 발급 → 스토리지 직접 PUT → 완료 등록으로 이어지는 업로드 파이프라인")
@RestController
@RequestMapping("/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final IssueUploadUrlsService issueUploadUrlsService;
    private final CompleteUploadService completeUploadService;

    @Operation(
        summary = "업로드 URL 발급",
        description = "파일 메타데이터를 검증하고 파일별로 스토리지 직접 업로드용 서명 URL을 발급한다. "
            + "미디어 행은 RESERVED 상태로 먼저 만들어진다. 파일 하나가 검증에 걸려도 요청 전체를 "
            + "실패시키지 않고 issued/rejected로 나눠 내려준다. "
            + "응답의 headers를 PUT 요청에 그대로 실어야 한다 — Content-Type이 서명 대상이라 "
            + "다른 값을 보내거나 생략하면 스토리지가 403으로 거부한다. "
            + "files가 비어 있으면 400, folderIds에 없는 폴더가 있으면 404, "
            + "업로드 권한이 방장 전용인 방에 비방장이 요청하면 403이 난다."
    )
    @PostMapping("/upload-urls")
    public ApiResponse<IssueUploadUrlsResponse> issueUploadUrls(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody IssueUploadUrlsRequest request
    ) {
        List<UploadFileCommand> files = request.files() == null ? null : request.files().stream()
            .map(file -> new UploadFileCommand(file.fileName(), file.mimeType(), file.size()))
            .toList();

        IssueUploadUrlsResult result =
            issueUploadUrlsService.issue(roomId, memberId, files, request.folderIds());
        return ApiResponse.of(IssueUploadUrlsResponse.from(result));
    }

    @Operation(
        summary = "업로드 완료 등록",
        description = "스토리지 PUT을 마친 미디어를 방 미디어로 등록한다. 서버가 스토리지에 실물이 있는지 "
            + "직접 확인하고, 발급 때 신고한 크기·MIME과 다르면 거부한다. 통과한 미디어는 "
            + "RESERVED에서 PROCESSING으로 넘어가고 SSE media.created 이벤트가 나간다. "
            + "항목별 실패는 failed로 내려주고 요청 전체를 실패시키지 않는다. "
            + "mediaIds가 비어 있으면 400, 남이 예약한 mediaId가 섞여 있으면 403이 난다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompleteUploadResponse> completeUpload(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId,
        @RequestBody CompleteUploadRequest request
    ) {
        CompleteUploadResult result =
            completeUploadService.complete(roomId, memberId, request.mediaIds());
        return ApiResponse.of(CompleteUploadResponse.from(result));
    }

}
