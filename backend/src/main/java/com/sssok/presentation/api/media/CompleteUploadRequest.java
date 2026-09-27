package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CompleteUploadRequest(
    @Schema(description = "스토리지 PUT 을 성공한 미디어 ID 목록")
    List<
        @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
        @Positive(message = "미디어 ID 목록이 올바르지 않습니다") Long> mediaIds
) {
}
