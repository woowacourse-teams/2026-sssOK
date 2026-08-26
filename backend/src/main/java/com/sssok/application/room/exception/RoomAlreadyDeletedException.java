package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class RoomAlreadyDeletedException extends SssOkException {

    public RoomAlreadyDeletedException() {
        super(ErrorCode.ROOM_ALREADY_DELETED);
    }
}
