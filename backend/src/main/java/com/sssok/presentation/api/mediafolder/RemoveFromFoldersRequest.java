package com.sssok.presentation.api.mediafolder;

import io.swagger.v3.oas.annotations.media.Schema;
import com.sssok.presentation.api.common.MediaSelectionRequest;
import java.util.List;

public record RemoveFromFoldersRequest(
    @Schema(description = "폴더에서 꺼낼 미디어 선택 범위", requiredMode = Schema.RequiredMode.REQUIRED)
    MediaSelectionRequest selection,

    @Schema(
        description = "꺼낼 대상 폴더 ID 목록. 생략하거나 빈 배열을 보내면 속한 모든 폴더에서 꺼내 루트로 보낸다. "
            + "지정하면 그 폴더들과의 관계만 끊고, 다른 폴더에 여전히 속해 있으면 루트로 가지 않는다.",
        example = "[31]"
    )
    List<Long> folderIds
) {
}
