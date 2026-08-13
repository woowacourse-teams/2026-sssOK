package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
// 만료 상태 — 입장·업로드 불가, 데이터는 아직 존재. 삭제로만 전이할 수 있다.
public final class ExpiredRoomStatus implements RoomStatus {

    public static final ExpiredRoomStatus INSTANCE = new ExpiredRoomStatus();

    private ExpiredRoomStatus() {
    }

    @Override
    public String name() {
        return "EXPIRED";
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
        return DeletedRoomStatus.INSTANCE;
    }

    @Override
    public RoomStatus toPurged() {
        throw new IllegalRoomStatusTransitionException(this, "PURGED");
    }
}
