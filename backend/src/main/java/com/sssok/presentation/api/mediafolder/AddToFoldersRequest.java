package com.sssok.presentation.api.mediafolder;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AddToFoldersRequest(
    @Schema(description = "담을 미디어 ID 목록", example = "[5012, 5011]")
    List<Long> mediaIds,

    @Schema(
        description = "담을 대상 폴더 ID 목록. 여기 담긴 모든 폴더 각각에 mediaIds의 모든 미디어를 담는다 "
            + "(카테시안 곱) — 예를 들어 mediaIds 2개 + folderIds 2개를 보내면 미디어-폴더 조합 4개가 생긴다. "
            + "폴더 하나에만 담고 싶으면 원소 1개짜리 배열([31])을 보내면 된다. "
            + "이미 속해 있던 다른 폴더는 그대로 유지되고, 이미 담겨 있던 조합은 alreadyInCount로만 집계된다(오류 아님).",
        example = "[31, 32]"
    )
    List<Long> folderIds
) {
}
