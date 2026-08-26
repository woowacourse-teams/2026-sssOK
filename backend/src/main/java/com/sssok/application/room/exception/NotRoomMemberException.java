package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class NotRoomMemberException extends SssOkException {

    public NotRoomMemberException() {
        super(ErrorCode.NOT_ROOM_MEMBER);
    }
}
