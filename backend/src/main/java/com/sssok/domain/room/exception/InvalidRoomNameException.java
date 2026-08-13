package com.sssok.domain.room.exception;

public class InvalidRoomNameException extends RuntimeException {

    public InvalidRoomNameException(String value) {
        super("올바르지 않은 방 이름입니다: " + value);
    }
}
