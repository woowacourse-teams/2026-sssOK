package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
// 소프트 삭제 상태 — 보관 기간 동안 머무는 상태. 영구 삭제로만 전이할 수 있다.
public final class DeletedRoomStatus implements RoomStatus {

    public static final DeletedRoomStatus INSTANCE = new DeletedRoomStatus();

    private DeletedRoomStatus() {
    }

    @Override
    public String name() {
        return "DELETED";
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
    public boolean isDeleted() {
        return true;
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
        return PurgedRoomStatus.INSTANCE;
    }
}
