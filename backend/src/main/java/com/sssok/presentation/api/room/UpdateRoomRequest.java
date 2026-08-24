package com.sssok.presentation.api.room;

import com.sssok.application.room.UpdateRoomCommand;

public record UpdateRoomRequest(String name, String uploadPolicy, Integer expiryHours) {

    private static final UpdateRoomRequest EMPTY = new UpdateRoomRequest(null, null, null);

    public static UpdateRoomRequest orEmpty(UpdateRoomRequest request) {
        return request == null ? EMPTY : request;
    }

    public UpdateRoomCommand toCommand() {
        return new UpdateRoomCommand(name, uploadPolicy, expiryHours);
    }
}
