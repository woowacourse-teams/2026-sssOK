package com.sssok.presentation.api.mediafolder;

import com.sssok.application.mediafolder.AddMediaToFoldersResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AddToFoldersResponse(
    @Schema(description = "새로 생긴 (미디어 x 폴더) 조합 수") int updatedCount,
    @Schema(description = "이미 담겨 있어 변화가 없던 조합 수") int alreadyInCount,
    @Schema(description = "존재하지 않아 건너뛴 미디어 ID") List<Long> notFoundMediaIds,
    @Schema(description = "변경 후 대상 폴더들의 최신 상태(칩의 개수 표시 갱신용)") List<FolderSummaryResponse> folders
) {

    public static AddToFoldersResponse from(AddMediaToFoldersResult result) {
        return new AddToFoldersResponse(
            result.updatedCount(),
            result.alreadyInCount(),
            result.notFoundMediaIds(),
            result.folders().stream().map(FolderSummaryResponse::from).toList()
        );
    }
}
