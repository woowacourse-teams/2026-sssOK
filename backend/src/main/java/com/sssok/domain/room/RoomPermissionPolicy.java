package com.sssok.domain.room;

import java.time.Instant;

// 방 설정 변경/삭제/업로드 권한 판정을 한곳에 모은 정책 객체.
// 전부 Room.hostId 비교로 판별되므로 Member 객체를 몰라도 된다.
public class RoomPermissionPolicy {

    public boolean canChangeSettings(Room room, Long requesterId) {
        return room.isHost(requesterId);
    }

    public boolean canDeleteRoom(Room room, Long requesterId) {
        return room.isHost(requesterId);
    }

    public boolean canUploadTo(Room room, Long requesterId, Instant now) {
        return room.canUpload(requesterId, now);
    }
}
