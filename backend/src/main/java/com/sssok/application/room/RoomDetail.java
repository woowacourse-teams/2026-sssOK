package com.sssok.application.room;

import com.sssok.domain.room.Room;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.util.List;

// 방 정보 + 요청자 관점의 부가 정보. status 는 조회 시각 기준으로 만료를 반영한 상태다.
public record RoomDetail(
    Room room,
    RoomStatus status,
    String hostName,
    boolean joined,
    int photoCount,
    List<RoomFolderSummary> folders
) {
}
