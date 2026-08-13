package com.sssok.domain.room;

import java.time.Instant;
import lombok.Getter;

// 방 애그리거트 루트. 코드, 상태, 만료, 업로드 권한을 관리한다.
// 이 단계(#17)에서는 생성/조회에 필요한 최소 형태만 다루고, 나머지 규칙은 #24에서 확장한다.
@Getter
public class Room {

    private final Long id;
    private final RoomCode code;
    private final RoomStatus status;
    private final Instant createdAt;

    private Room(Long id, RoomCode code, RoomStatus status, Instant createdAt) {
        this.id = id;
        this.code = code;
        this.status = status;
        this.createdAt = createdAt;
    }

    // 신규 생성 (아직 저장 전이라 id 없음)
    public static Room create(RoomCode code, Instant now) {
        return new Room(null, code, RoomStatus.ACTIVE, now);
    }

    // 저장소에서 불러온 값으로 복원
    public static Room reconstruct(Long id, RoomCode code, RoomStatus status, Instant createdAt) {
        return new Room(id, code, status, createdAt);
    }
}
