package com.sssok.application.room;

import java.time.Instant;

// SSE로 발행되는 "room.deleted" 이벤트 payload
public record RoomDeletedEvent(Long roomId, Instant deletedAt, Instant purgeAt) {
}
