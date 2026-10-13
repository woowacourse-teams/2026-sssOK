package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;
// 만료 상태 — 입장·업로드 불가, 데이터는 아직 존재. 삭제로만 전이할 수 있다.
// 만료 기한이 없어져 새로 이 상태가 되는 방은 없고, 기한이 있던 시절에 만료된 방만 이 상태로 남아 있다.
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
    public boolean isDeleted() {
        return false;
    }

    @Override
    public RoomStatus toDeleted() {
        return DeletedRoomStatus.INSTANCE;
    }
}
