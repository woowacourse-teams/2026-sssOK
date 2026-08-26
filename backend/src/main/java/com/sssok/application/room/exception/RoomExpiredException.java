package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class RoomExpiredException extends SssOkException {

    public RoomExpiredException() {
        super(ErrorCode.ROOM_EXPIRED);
    }
}
