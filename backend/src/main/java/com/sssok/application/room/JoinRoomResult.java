package com.sssok.application.room;

import com.sssok.domain.member.Member;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;

public record JoinRoomResult(
    Long roomId,
    Long userId,
    String displayName,
    Long hostId,
    Instant joinedAt,
    boolean newlyJoined
) {

    public static JoinRoomResult newlyJoined(Room room, RoomMember roomMember, Member member) {
        return of(room, roomMember, member, true);
    }

    public static JoinRoomResult rejoined(Room room, RoomMember roomMember, Member member) {
        return of(room, roomMember, member, false);
    }

    private static JoinRoomResult of(Room room, RoomMember roomMember, Member member, boolean newlyJoined) {
        return new JoinRoomResult(
            room.getId(),
            member.getId(),
            member.getDisplayName().value(),
            room.getHostId(),
            roomMember.getJoinedAt(),
            newlyJoined
        );
    }
}
