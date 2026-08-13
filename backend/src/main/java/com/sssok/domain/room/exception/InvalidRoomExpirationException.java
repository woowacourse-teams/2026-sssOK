package com.sssok.domain.room.exception;

public class InvalidRoomExpirationException extends RuntimeException {

    public InvalidRoomExpirationException(Object value) {
        super("올바르지 않은 방 만료 시각입니다: " + value);
    }
}
