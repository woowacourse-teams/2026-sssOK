package com.sssok.presentation.api.folder;

import com.sssok.domain.folder.Folder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FolderResponse(
    @Schema(description = "폴더 식별자") Long id,
    @Schema(description = "폴더 이름") String name,
    @Schema(description = "폴더 생성 시각") Instant createdAt,
    @Schema(description = "폴더에 담긴 사진 수") int photoCount
) {

    public static FolderResponse from(Folder folder, int photoCount) {
        return new FolderResponse(folder.getId(), folder.getName().value(), folder.getCreatedAt(), photoCount);
    }
}
