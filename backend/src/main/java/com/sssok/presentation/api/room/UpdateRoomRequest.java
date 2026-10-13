package com.sssok.presentation.api.room;

import com.sssok.application.room.UpdateRoomCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateRoomRequest(
    @Schema(description = "바꿀 방 이름 (최대 12자). 안 바꾸려면 생략", example = "2차 회식") String name,
    @Schema(description = "바꿀 업로드 권한. everyone(누구나) 또는 host(방장만). 안 바꾸려면 생략", example = "host") String uploadPolicy
) {

    private static final UpdateRoomRequest EMPTY = new UpdateRoomRequest(null, null);

    public static UpdateRoomRequest orEmpty(UpdateRoomRequest request) {
        return request == null ? EMPTY : request;
    }

    public UpdateRoomCommand toCommand() {
        return new UpdateRoomCommand(name, uploadPolicy);
    }
}
