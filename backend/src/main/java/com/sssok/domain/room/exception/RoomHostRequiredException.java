package com.sssok.domain.room.exception;

public class RoomHostRequiredException extends RuntimeException {

    public RoomHostRequiredException() {
        super("방장만 수행할 수 있는 작업입니다.");
    }
}
