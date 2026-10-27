package com.sssok.renewal.application.media.upload.shared;

import java.util.Map;
import java.time.Duration;
import com.sssok.domain.file.StoredFile;

// 서명 URL 과 함께, PUT 할 때 반드시 실어야 하는 헤더를 같이 내려준다.
// Content-Type 이 서명 대상이라 프론트가 추측하면 업로드가 깨진다.
public record UploadUrl(
        Long mediaId,
        String fileName,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        int expiresIn
) {
    public static UploadUrl of(StoredFile file, String presignedUrl, Duration validityDuration) {
        return new UploadUrl(file.getId(), file.getOriginalFileName(), presignedUrl, "PUT",
            Map.of("Content-Type", file.getMediaType().contentType()), (int) validityDuration.toSeconds());
    }
}
