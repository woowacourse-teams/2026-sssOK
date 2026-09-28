package com.sssok.presentation.api.mediafolder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AddToFoldersRequest(
    // 빈 배열은 "변경 0건으로 성공"이 계약이라 @NotEmpty 를 붙이지 않는다.
    @Schema(description = "폴더에 담을 미디어 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
    List<
        @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
        @Positive(message = "미디어 ID 목록이 올바르지 않습니다") Long> mediaIds,

    // 값의 범위는 검사하지 않는다. 없는 폴더는 404 로 알려주는 것이 이 API 의 계약이다.
    @Schema(
        description = "담을 대상 폴더 ID. 선택된 모든 미디어를 이 폴더 하나에 담는다. "
            + "이미 속해 있던 다른 폴더는 그대로 유지되고, 이미 이 폴더에 담겨 있던 미디어는 alreadyInCount로만 집계된다(오류 아님).",
        example = "31"
    )
    @NotNull(message = "미디어와 폴더를 선택해 주세요")
    Long folderId
) {
}
