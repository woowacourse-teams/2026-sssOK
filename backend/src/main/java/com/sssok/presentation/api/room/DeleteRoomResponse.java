package com.sssok.presentation.api.room;

import com.sssok.application.room.DeleteRoomResult;
import java.time.Instant;

public record DeleteRoomResponse(Instant deletedAt, Instant purgeAt) {

    public static DeleteRoomResponse from(DeleteRoomResult result) {
        return new DeleteRoomResponse(result.deletedAt(), result.purgeAt());
    }
}
