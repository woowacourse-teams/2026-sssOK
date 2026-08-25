package com.sssok.application.room.exception;

public class RoomMembershipRequiredException extends RuntimeException {

    public RoomMembershipRequiredException() {
        super("입장한 방만 구독할 수 있습니다");
    }
}
