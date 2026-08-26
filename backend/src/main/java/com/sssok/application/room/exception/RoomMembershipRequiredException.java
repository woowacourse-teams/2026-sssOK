package com.sssok.application.room.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class RoomMembershipRequiredException extends SssOkException {

    public RoomMembershipRequiredException() {
        super(ErrorCode.ROOM_MEMBERSHIP_REQUIRED);
    }
}
