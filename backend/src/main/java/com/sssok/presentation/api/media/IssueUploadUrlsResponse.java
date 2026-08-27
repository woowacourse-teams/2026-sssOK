package com.sssok.presentation.api.media;

import com.sssok.application.media.IssueUploadUrlsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record IssueUploadUrlsResponse(
    @Schema(description = "발급 성공한 파일 목록") List<IssuedResponse> issued,
    @Schema(description = "검증에서 걸러진 파일 목록") List<RejectedResponse> rejected
) {
    public static IssueUploadUrlsResponse from(IssueUploadUrlsResult result) {
        return new IssueUploadUrlsResponse(
            result.issued().stream()
                .map(url -> new IssuedResponse(url.mediaId(), url.fileName(), url.uploadUrl(),
                    url.method(), url.headers(), url.expiresIn()))
                .toList(),
            result.rejected().stream()
                .map(file -> new RejectedResponse(file.fileName(), file.code(), file.message()))
                .toList()
        );
    }

    public record IssuedResponse(
        @Schema(description = "예약 생성된 미디어 ID. 완료 등록에 쓴다") Long mediaId,
        @Schema(description = "요청한 원본 파일명") String fileName,
        @Schema(description = "스토리지 직접 업로드용 서명 URL") String uploadUrl,
        @Schema(description = "업로드 시 사용할 HTTP 메서드", example = "PUT") String method,
        @Schema(description = "PUT 요청에 그대로 포함해야 하는 헤더") Map<String, String> headers,
        @Schema(description = "서명 URL 유효 시간(초)", example = "600") int expiresIn
    ) {
    }

    public record RejectedResponse(
        @Schema(description = "원본 파일명") String fileName,
        @Schema(description = "FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE / INVALID_PARAM") String code,
        @Schema(description = "사용자에게 보여줄 실패 사유") String message
    ) {
    }
}
