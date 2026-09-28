package com.sssok.presentation.api.folder;

import io.swagger.v3.oas.annotations.media.Schema;

// 이름 규칙은 FolderName 값 객체가 갖는다. @NotBlank·@Size 를 겹치면 응답 코드가
// INVALID_FOLDER_NAME·FOLDER_NAME_TOO_LONG 에서 INVALID_PARAM 으로 뭉개진다.
public record CreateFolderRequest(
    @Schema(description = "폴더 이름 (최대 12자, 앞뒤 공백 제거)", example = "맛집") String name
) {
}
