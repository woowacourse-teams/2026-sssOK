package com.sssok.application.room;

// 재참여한 새 연결을 종료하지 않도록 삭제한 참여 기록 ID를 전달한다.
public record RoomMemberLeftEvent(Long roomId, Long membershipId) {
}
