package com.sssok.presentation.api.room;

import com.sssok.application.room.RoomFolderSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RoomFolderResponse(
    @Schema(description = "폴더 식별자") Long id,
    @Schema(description = "폴더 이름") String name,
    @Schema(description = "폴더 생성 시각") Instant createdAt,
    @Schema(description = "폴더에 담긴 사진 수") int photoCount
) {

    public static RoomFolderResponse from(RoomFolderSummary summary) {
        return new RoomFolderResponse(summary.id(), summary.name(), summary.createdAt(), summary.photoCount());
    }
}
