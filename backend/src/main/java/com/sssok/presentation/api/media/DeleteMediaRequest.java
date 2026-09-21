package com.sssok.presentation.api.media;

import com.sssok.presentation.api.common.MediaSelectionRequest;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteMediaRequest(
    @Schema(description = "삭제할 미디어 선택 범위", requiredMode = Schema.RequiredMode.REQUIRED)
    MediaSelectionRequest selection
) {
}
