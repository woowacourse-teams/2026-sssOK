package com.sssok.presentation.api.media;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DeleteMediaRequest(
    @Schema(description = "삭제할 미디어 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<Long> mediaIds
) {
}
