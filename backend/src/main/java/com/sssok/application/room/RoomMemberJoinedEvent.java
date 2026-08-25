package com.sssok.application.room;

import java.time.Instant;

// SSE로 발행되는 "room.member.joined" 이벤트 payload
public record RoomMemberJoinedEvent(Long roomId, Long memberId, String displayName, Instant joinedAt) {

    public static RoomMemberJoinedEvent from(JoinRoomResult result) {
        return new RoomMemberJoinedEvent(result.roomId(), result.userId(), result.displayName(), result.joinedAt());
    }
}
