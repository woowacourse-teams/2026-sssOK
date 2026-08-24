package com.sssok.presentation.api.room;

public record JoinRoomRequest(String passcode) {

    private static final JoinRoomRequest EMPTY = new JoinRoomRequest(null);

    public static JoinRoomRequest orEmpty(JoinRoomRequest request) {
        return request == null ? EMPTY : request;
    }
}
