package com.sssok.presentation.api.room;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomRequest(
    @Schema(description = "방 이름 (최대 12자)", example = "우테코 회식") String name
) {
}
