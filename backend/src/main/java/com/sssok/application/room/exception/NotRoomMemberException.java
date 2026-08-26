package com.sssok.application.room.exception;

public class NotRoomMemberException extends RuntimeException {

    public NotRoomMemberException() {
        super("입장한 방에서만 이용할 수 있습니다");
    }
}
