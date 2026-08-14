package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
// 영구 삭제 완료 상태 — 종단 상태, 더 이상 전이할 수 없다.
public final class PurgedRoomStatus implements RoomStatus {

    public static final PurgedRoomStatus INSTANCE = new PurgedRoomStatus();

    private PurgedRoomStatus() {
    }

    @Override
    public String name() {
        return "PURGED";
    }

    @Override
    public boolean canEnter() {
        return false;
    }

    @Override
    public boolean canUpload(UploadPolicy uploadPolicy, boolean requesterIsHost) {
        return false;
    }

    @Override
    public RoomStatus toExpired() {
        throw new IllegalRoomStatusTransitionException(this, "EXPIRED");
    }

    @Override
    public RoomStatus toDeleted() {
        throw new IllegalRoomStatusTransitionException(this, "DELETED");
    }

    @Override
    public RoomStatus toPurged() {
        throw new IllegalRoomStatusTransitionException(this, "PURGED");
    }
}
