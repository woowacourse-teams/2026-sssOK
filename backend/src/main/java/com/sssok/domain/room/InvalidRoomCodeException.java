package com.sssok.domain.room;

public class InvalidRoomCodeException extends RuntimeException {

    public InvalidRoomCodeException(String value) {
        super("올바르지 않은 방 코드 형식입니다: " + value);
    }
}
