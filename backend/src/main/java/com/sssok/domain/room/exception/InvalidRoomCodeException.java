package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidRoomCodeException extends SssOkException {

    public InvalidRoomCodeException(String value) {
        super(ErrorCode.INVALID_ROOM_CODE, value);
    }
}
