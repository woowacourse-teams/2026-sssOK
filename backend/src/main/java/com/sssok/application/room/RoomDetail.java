package com.sssok.application.room;

import com.sssok.domain.room.Room;

// 방 정보 + 요청자 관점의 부가 정보
public record RoomDetail(Room room, String hostName, boolean joined) {
}
