package com.sssok.presentation.api.room;

import com.sssok.domain.room.Room;
import java.time.Instant;

public record RoomResponse(String code, String status, Instant createdAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getCode().value(), room.getStatus().name(), room.getCreatedAt());
    }
}
