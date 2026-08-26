package com.sssok.application.room;

import java.time.Instant;

// 방 조회 응답에 함께 실리는 폴더 요약 정보.
public record RoomFolderSummary(Long id, String name, Instant createdAt, int photoCount) {
}
