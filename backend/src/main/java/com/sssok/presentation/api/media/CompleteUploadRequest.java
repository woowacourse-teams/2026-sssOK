package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CompleteUploadRequest(
    @Schema(description = "스토리지 PUT 을 성공한 미디어 ID 목록") List<Long> mediaIds
) {
}
