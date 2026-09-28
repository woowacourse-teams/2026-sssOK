package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record DeleteMediaRequest(
    // 빈 배열은 "삭제 0건으로 성공"이 계약이라 @NotEmpty 를 붙이지 않는다.
    @Schema(description = "삭제할 미디어 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
    List<
        @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
        @Positive(message = "미디어 ID 목록이 올바르지 않습니다") Long> mediaIds
) {
}
