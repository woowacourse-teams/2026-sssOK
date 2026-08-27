package com.sssok.presentation.api.media;

import com.sssok.application.media.ReissuedUploadUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record ReissueUploadUrlResponse(
    @Schema(description = "미디어 ID. 재발급해도 바뀌지 않는다") Long mediaId,
    String fileName,
    @Schema(description = "새로 발급된 서명 URL") String uploadUrl,
    String method,
    Map<String, String> headers,
    @Schema(description = "서명 URL 유효 시간(초)") int expiresIn,
    @Schema(description = "이번 호출을 포함한 누적 재발급 횟수") int retryCount,
    @Schema(description = "허용 최대 재발급 횟수") int maxRetryCount
) {
    public static ReissueUploadUrlResponse from(ReissuedUploadUrl url) {
        return new ReissueUploadUrlResponse(url.mediaId(), url.fileName(), url.uploadUrl(),
            url.method(), url.headers(), url.expiresIn(), url.retryCount(), url.maxRetryCount());
    }
}
