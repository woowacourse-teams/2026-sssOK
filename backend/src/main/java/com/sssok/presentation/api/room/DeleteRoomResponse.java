package com.sssok.presentation.api.room;

import com.sssok.application.room.DeleteRoomResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record DeleteRoomResponse(
    @Schema(description = "삭제(soft delete) 처리된 시각") Instant deletedAt
) {

    public static DeleteRoomResponse from(DeleteRoomResult result) {
        return new DeleteRoomResponse(result.deletedAt());
    }
}
