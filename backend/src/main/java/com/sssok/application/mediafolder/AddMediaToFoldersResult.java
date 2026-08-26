package com.sssok.application.mediafolder;

import java.util.List;

public record AddMediaToFoldersResult(
    int updatedCount,
    int alreadyInCount,
    List<Long> notFoundMediaIds,
    List<FolderSummary> folders
) {
}
