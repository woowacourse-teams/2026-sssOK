package com.sssok.presentation.api.mediafolder;

import io.swagger.v3.oas.annotations.media.Schema;
import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.presentation.api.common.MediaSelectionRequest;

public record AddToFoldersRequest(
    @Schema(description = "폴더에 담을 미디어 선택 범위", requiredMode = Schema.RequiredMode.REQUIRED)
    MediaSelectionRequest selection,

    @Schema(
        description = "담을 대상 폴더 ID. 선택된 모든 미디어를 이 폴더 하나에 담는다. "
            + "이미 속해 있던 다른 폴더는 그대로 유지되고, 이미 이 폴더에 담겨 있던 미디어는 alreadyInCount로만 집계된다(오류 아님).",
        example = "31"
    )
    Long folderId,

    @Schema(description = "업로더 필터. ALL / ME / OTHERS, 생략 시 ALL")
    MediaUploaderFilter uploader
) {
}
