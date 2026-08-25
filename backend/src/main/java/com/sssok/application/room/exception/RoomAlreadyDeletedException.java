package com.sssok.application.room.exception;

public class RoomAlreadyDeletedException extends RuntimeException {

    public RoomAlreadyDeletedException() {
        super("이미 삭제되었거나 만료된 방입니다");
    }
}
