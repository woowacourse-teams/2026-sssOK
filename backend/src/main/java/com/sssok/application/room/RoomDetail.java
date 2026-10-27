package com.sssok.application.room;

import com.sssok.domain.room.Room;
import java.util.List;

// 방 정보 + 요청자 관점의 부가 정보.
public record RoomDetail(
    Room room,
    String hostName,
    boolean joined,
    int photoCount,
    List<RoomFolderSummary> folders,
    String myNickname
) {
    public RoomDetail(Room room, String hostName, boolean joined, int photoCount, List<RoomFolderSummary> folders) {
        this(room, hostName, joined, photoCount, folders, null);
    }
}
