package com.sssok.presentation.api.mediafolder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record RemoveFromFoldersRequest(
    @Schema(description = "폴더에서 꺼낼 미디어 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
    List<
        @NotNull(message = "미디어 ID 목록이 올바르지 않습니다")
        @Positive(message = "미디어 ID 목록이 올바르지 않습니다") Long> mediaIds,

    // 목록 자체는 생략할 수 있지만 원소가 null 이면 "없는 폴더"가 아니라 잘못된 요청이다.
    @Schema(
        description = "꺼낼 대상 폴더 ID 목록. 생략하거나 빈 배열을 보내면 속한 모든 폴더에서 꺼내 루트로 보낸다. "
            + "지정하면 그 폴더들과의 관계만 끊고, 다른 폴더에 여전히 속해 있으면 루트로 가지 않는다.",
        example = "[31]"
    )
    List<@NotNull(message = "폴더 ID 목록이 올바르지 않습니다") Long> folderIds
) {
}
