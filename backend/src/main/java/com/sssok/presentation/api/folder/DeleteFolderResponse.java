package com.sssok.presentation.api.folder;

import com.sssok.application.folder.DeleteFolderResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteFolderResponse(
    @Schema(description = "삭제된 폴더 식별자") Long deletedFolderId,
    @Schema(description = "이 폴더에서 떨어져 나와 루트로 옮겨진 사진 수") int detachedPhotoCount
) {

    public static DeleteFolderResponse from(DeleteFolderResult result) {
        return new DeleteFolderResponse(result.deletedFolderId(), result.detachedPhotoCount());
    }
}
