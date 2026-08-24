package com.sssok.presentation.api.room;

import com.sssok.application.room.JoinRoomResult;
import java.time.Instant;

public record RoomMemberResponse(
    Long roomId,
    Long userId,
    String displayName,
    Long hostId,
    Instant joinedAt
) {

    public static RoomMemberResponse from(JoinRoomResult result) {
        return new RoomMemberResponse(
            result.roomId(),
            result.userId(),
            result.displayName(),
            result.hostId(),
            result.joinedAt()
        );
    }
}
