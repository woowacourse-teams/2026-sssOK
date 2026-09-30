package com.sssok.domain.room.roomstatus;

import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
// 소프트 삭제 상태 — 입장·업로드 불가, 데이터는 계속 보관된다. 종단 상태라 더 이상 전이할 수 없다.
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
}
