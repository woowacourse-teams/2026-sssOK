package com.sssok.domain.room.exception;

import com.sssok.domain.room.roomstatus.RoomStatus;

public class IllegalRoomStatusTransitionException extends RuntimeException {

    public IllegalRoomStatusTransitionException(RoomStatus from, String to) {
        super("허용되지 않는 상태 전이입니다: " + from.name() + " -> " + to);
    }
}
