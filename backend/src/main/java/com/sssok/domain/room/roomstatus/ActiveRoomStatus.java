package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
// 활성 상태 — 입장·업로드 가능, 만료·삭제로만 전이할 수 있다.
public final class ActiveRoomStatus implements RoomStatus {

    public static final ActiveRoomStatus INSTANCE = new ActiveRoomStatus();

    private ActiveRoomStatus() {
    }

    @Override
    public String name() {
        return "ACTIVE";
    }

    @Override
    public boolean canEnter() {
        return true;
    }

    @Override
    public boolean canUpload(UploadPolicy uploadPolicy, boolean requesterIsHost) {
        return uploadPolicy.allows(requesterIsHost);
    }

    @Override
    public boolean isDeleted() {
        return false;
    }

    @Override
    public RoomStatus toExpired() {
        return ExpiredRoomStatus.INSTANCE;
    }

    @Override
    public RoomStatus toDeleted() {
        return DeletedRoomStatus.INSTANCE;
    }

    @Override
    public RoomStatus toPurged() {
        throw new IllegalRoomStatusTransitionException(this, "PURGED");
    }
}
