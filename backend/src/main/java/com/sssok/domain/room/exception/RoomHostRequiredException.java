package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class RoomHostRequiredException extends SssOkException {

    public RoomHostRequiredException() {
        super(ErrorCode.NOT_ROOM_HOST);
    }
}
