package com.sssok.presentation.api.mediafolder;

import com.sssok.application.mediafolder.RemoveMediaFromFoldersResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RemoveFromFoldersResponse(
    @Schema(description = "실제로 제거된 (미디어 x 폴더) 조합 수") int updatedCount,
    @Schema(description = "이 요청으로 속한 폴더가 0개가 되어 루트로 간 미디어 ID") List<Long> movedToRootMediaIds,
    @Schema(description = "존재하지 않아 건너뛴 미디어 ID") List<Long> notFoundMediaIds,
    @Schema(description = "변경 후 대상 폴더들의 최신 상태") List<FolderSummaryResponse> folders
) {

    public static RemoveFromFoldersResponse from(RemoveMediaFromFoldersResult result) {
        return new RemoveFromFoldersResponse(
            result.updatedCount(),
            result.movedToRootMediaIds(),
            result.notFoundMediaIds(),
            result.folders().stream().map(FolderSummaryResponse::from).toList()
        );
    }
}
