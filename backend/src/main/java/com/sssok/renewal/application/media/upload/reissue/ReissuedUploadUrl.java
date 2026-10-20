package com.sssok.renewal.application.media.upload.reissue;

import com.sssok.renewal.application.media.upload.shared.UploadUrl;
import java.util.Map;

public record ReissuedUploadUrl(Long mediaId, String fileName, String uploadUrl, String method,
                                Map<String, String> headers, int expiresIn,
                                int retryCount, int maxRetryCount) {
    public static ReissuedUploadUrl of(UploadUrl uploadUrl, int retryCount, int maxRetryCount) {
        return new ReissuedUploadUrl(uploadUrl.mediaId(), uploadUrl.fileName(), uploadUrl.uploadUrl(),
            uploadUrl.method(), uploadUrl.headers(), uploadUrl.expiresIn(), retryCount, maxRetryCount);
    }
}
