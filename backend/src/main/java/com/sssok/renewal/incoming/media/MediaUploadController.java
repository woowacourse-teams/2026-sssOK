package com.sssok.renewal.incoming.media;

import com.sssok.renewal.outgoing.monitoring.UploadMetrics;
import com.sssok.renewal.application.media.upload.complete.CompleteUploadService;
import com.sssok.renewal.application.media.upload.complete.CompleteUploadResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.sssok.renewal.application.media.upload.reissue.ReissueUploadUrlCommand;
import com.sssok.renewal.application.media.upload.reissue.ReissueUploadUrlService;
import com.sssok.renewal.application.media.upload.reissue.ReissuedUploadUrl;
import com.sssok.renewal.application.media.upload.issue.IssueUploadUrlsResult;
import com.sssok.renewal.application.media.upload.issue.IssueUploadUrlsService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "미디어 업로드")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/media")
@RequiredArgsConstructor
public class MediaUploadController {
    private final UploadMetrics metrics;
    private final CompleteUploadService completeUploadService;
    private final IssueUploadUrlsService issueUploadUrlsService;
    private final ReissueUploadUrlService reissueUploadUrlService;

    @Operation(summary = "업로드 URL 발급")
    @PostMapping("/upload-urls")
    public ApiResponse<IssueUploadUrlsResponse> issueUploadUrls(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @Valid @RequestBody IssueUploadUrlsRequest request
    ) {
        IssueUploadUrlsResult result = issueUploadUrlsService.issue(request.toCommand(roomId, memberId));
        metrics.issued(result.issued().size(), result.rejected().size());
        return ApiResponse.of(IssueUploadUrlsResponse.from(result));
    }

    @Operation(summary = "업로드 URL 재발급")
    @PostMapping("/{mediaId}/upload-url")
    public ApiResponse<ReissueUploadUrlResponse> reissueUploadUrl(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @PathVariable Long mediaId,
        @Valid @RequestBody(required = false) ReissueUploadUrlRequest request
    ) {
        ReissueUploadUrlCommand command = request == null
            ? new ReissueUploadUrlCommand(roomId, mediaId, memberId, null)
            : request.toCommand(roomId, mediaId, memberId);
        ReissuedUploadUrl result = reissueUploadUrlService.reissue(command);
        return ApiResponse.of(ReissueUploadUrlResponse.from(result));
    }

    @Operation(summary = "업로드 완료 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompleteUploadResponse> completeUpload(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @PathVariable Long roomId,
        @Valid @RequestBody CompleteUploadRequest request
    ) {
        CompleteUploadResult result = completeUploadService.complete(request.toCommand(roomId, memberId));
        metrics.registered(result.registered().size(), result.failed().size());
        return ApiResponse.of(CompleteUploadResponse.from(result));
    }
}
