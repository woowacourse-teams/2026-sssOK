package com.sssok.domain.room;

// 방의 생명주기 상태(활성/만료/삭제/영구삭제).
// 이 단계(#17)에서는 생성 직후 상태만 다루고, 전체 전이 규칙은 #24에서 확장한다.
public enum RoomStatus {
    ACTIVE
}
