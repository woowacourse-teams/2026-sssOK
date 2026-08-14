package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;

// 방의 생명주기 상태 (GoF 상태 패턴).
// 각 구현체는 자기가 허용하는 전이만 정의하고, 그 외의 전이를 시도하면 예외를 던진다.
// 상태별 인스턴스는 하나씩만 존재한다 (싱글톤) — Room 여러 개가 같은 ACTIVE 상태 객체를 공유한다.
public interface RoomStatus {

    String name();

    boolean canEnter();

    boolean canUpload(UploadPolicy uploadPolicy, boolean requesterIsHost);

    RoomStatus toExpired();

    RoomStatus toDeleted();

    RoomStatus toPurged();

    static RoomStatus initial() {
        return ActiveRoomStatus.INSTANCE;
    }

    static RoomStatus from(String name) {
        return switch (name) {
            case "ACTIVE" -> ActiveRoomStatus.INSTANCE;
            case "EXPIRED" -> ExpiredRoomStatus.INSTANCE;
            case "DELETED" -> DeletedRoomStatus.INSTANCE;
            case "PURGED" -> PurgedRoomStatus.INSTANCE;
            default -> throw new IllegalArgumentException("알 수 없는 방 상태입니다: " + name);
        };
    }
}
