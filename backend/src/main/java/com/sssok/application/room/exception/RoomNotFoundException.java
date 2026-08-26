package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.room.RoomCode;

public class RoomNotFoundException extends SssOkException {

    public RoomNotFoundException(RoomCode code) {
        super(ErrorCode.ROOM_NOT_FOUND, code.value());
    }

    public RoomNotFoundException(Long roomId) {
        super(ErrorCode.ROOM_NOT_FOUND, roomId);
    }
}
