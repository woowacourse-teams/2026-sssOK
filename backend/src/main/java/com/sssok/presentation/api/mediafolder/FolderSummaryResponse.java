package com.sssok.presentation.api.mediafolder;

import com.sssok.application.mediafolder.FolderSummary;
import io.swagger.v3.oas.annotations.media.Schema;

public record FolderSummaryResponse(
    @Schema(description = "폴더 식별자") Long id,
    @Schema(description = "폴더 이름") String name,
    @Schema(description = "폴더에 속한 미디어 수") int photoCount
) {

    public static FolderSummaryResponse from(FolderSummary summary) {
        return new FolderSummaryResponse(summary.id(), summary.name(), summary.photoCount());
    }
}
