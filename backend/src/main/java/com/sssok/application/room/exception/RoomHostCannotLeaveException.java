package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class RoomHostCannotLeaveException extends SssOkException {
    public RoomHostCannotLeaveException() {
        super(ErrorCode.ROOM_HOST_CANNOT_LEAVE);
    }
}
