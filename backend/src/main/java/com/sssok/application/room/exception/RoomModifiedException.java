package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class RoomModifiedException extends SssOkException {

    public RoomModifiedException() {
        super(ErrorCode.ROOM_MODIFIED);
    }
}
