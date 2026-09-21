package com.sssok.presentation.api.media;

import com.sssok.presentation.api.common.MediaSelectionRequest;
import com.sssok.application.media.MediaUploaderFilter;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteMediaRequest(
    @Schema(description = "삭제할 미디어 선택 범위", requiredMode = Schema.RequiredMode.REQUIRED)
    MediaSelectionRequest selection,
    @Schema(description = "삭제 대상을 제한할 폴더 ID. 생략하면 방 전체에서 선택") Long folderId,
    @Schema(description = "업로더 필터. ALL / ME / OTHERS, 생략 시 ALL") MediaUploaderFilter uploader
) {
}
