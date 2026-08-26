package com.sssok.domain.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;
import com.sssok.domain.room.roomstatus.RoomStatus;

public class IllegalRoomStatusTransitionException extends SssOkException {

    public IllegalRoomStatusTransitionException(RoomStatus from, String to) {
        super(ErrorCode.ILLEGAL_ROOM_STATUS_TRANSITION, from.name(), to);
    }
}
