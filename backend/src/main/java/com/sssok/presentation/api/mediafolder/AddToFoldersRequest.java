package com.sssok.presentation.api.mediafolder;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AddToFoldersRequest(
    @Schema(description = "폴더에 담을 미디어 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<Long> mediaIds,

    @Schema(
        description = "담을 대상 폴더 ID. 선택된 모든 미디어를 이 폴더 하나에 담는다. "
            + "이미 속해 있던 다른 폴더는 그대로 유지되고, 이미 이 폴더에 담겨 있던 미디어는 alreadyInCount로만 집계된다(오류 아님).",
        example = "31"
    )
    Long folderId
) {
}
