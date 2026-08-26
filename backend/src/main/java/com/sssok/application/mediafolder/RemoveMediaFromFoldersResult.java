package com.sssok.application.mediafolder;

import java.util.List;

public record RemoveMediaFromFoldersResult(
    int updatedCount,
    List<Long> movedToRootMediaIds,
    List<Long> notFoundMediaIds,
    List<FolderSummary> folders
) {
}
