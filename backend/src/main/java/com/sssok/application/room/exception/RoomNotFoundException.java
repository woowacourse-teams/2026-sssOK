package com.sssok.application.room.exception;

import com.sssok.domain.room.RoomCode;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(RoomCode code) {
        super("존재하지 않는 방입니다: " + code.value());
    }
}
