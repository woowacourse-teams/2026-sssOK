package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class InvalidRoomExpirationException extends SssOkException {

    public InvalidRoomExpirationException() {
        super(ErrorCode.INVALID_ROOM_EXPIRATION);
    }
}
