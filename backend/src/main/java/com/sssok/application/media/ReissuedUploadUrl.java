package com.sssok.application.media;

import java.util.Map;

public record ReissuedUploadUrl(Long mediaId, String fileName, String uploadUrl, String method,
                                Map<String, String> headers, int expiresIn,
                                int retryCount, int maxRetryCount) {
}
