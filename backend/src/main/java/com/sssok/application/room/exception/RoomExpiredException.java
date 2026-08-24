package com.sssok.application.room.exception;

public class RoomExpiredException extends RuntimeException {

    public RoomExpiredException() {
        super("이미 사라진 방입니다");
    }
}
