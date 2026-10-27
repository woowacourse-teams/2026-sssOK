package com.sssok.application.room;

// 저장소에서 읽은 방 목록 정보. 썸네일 키는 서비스에서 접근 URL로 변환한다.
public record RoomMembershipSummary(
    Long roomId,
    String code,
    String name,
    boolean isHost,
    long mediaCount,
    long memberCount,
    String thumbnailKey
) {
}
