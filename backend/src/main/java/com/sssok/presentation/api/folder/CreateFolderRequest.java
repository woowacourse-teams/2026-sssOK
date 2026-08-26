package com.sssok.presentation.api.folder;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateFolderRequest(
    @Schema(description = "폴더 이름 (최대 12자, 앞뒤 공백 제거)", example = "맛집") String name
) {
}
