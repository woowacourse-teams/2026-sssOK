package com.sssok.presentation.api.room;

import com.sssok.application.room.RoomDetail;
import com.sssok.domain.room.Room;
import java.time.Instant;

public record RoomResponse(
    Long roomId,
    String code,
    String name,
    String status,
    Long hostId,
    String hostName,
    String uploadPolicy,
    boolean joined,
    Instant expiresAt,
    Instant createdAt
) {

    public static RoomResponse from(RoomDetail detail) {
        Room room = detail.room();
        return new RoomResponse(
            room.getId(),
            room.getCode().value(),
            room.getName().value(),
            room.getStatus().name(),
            room.getHostId(),
            detail.hostName(),
            room.getUploadPolicy().apiValue(),
            detail.joined(),
            room.getExpiration().expiresAt(),
            room.getCreatedAt()
        );
    }
}
