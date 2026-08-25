package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomNameException;
// 방 이름 값 객체.
public record RoomName(String value) {

    private static final int MAX_LENGTH = 12;

    public RoomName {
        if (value == null || value.isBlank()) {
            throw new InvalidRoomNameException(value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidRoomNameException(value);
        }
    }
}
