package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidRoomNameException extends SssOkException {

    public InvalidRoomNameException(String value) {
        super(ErrorCode.INVALID_ROOM_NAME, value);
    }
}
