package com.sssok.application.room;

import com.sssok.domain.room.Room;
import java.time.Instant;

// SSE로 발행되는 "room.updated" 이벤트 payload. 변경된 필드만이 아니라 patch 적용 후 최종 상태 전체를 담는다.
public record RoomUpdatedEvent(Long roomId, String name, String uploadPolicy, Instant expiresAt) {

    public static RoomUpdatedEvent from(Room room) {
        return new RoomUpdatedEvent(
            room.getId(),
            room.getName().value(),
            room.getUploadPolicy().apiValue(),
            room.getExpiration().expiresAt()
        );
    }
}
