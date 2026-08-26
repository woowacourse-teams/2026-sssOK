package com.sssok.presentation.api.room;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomRequest(
    @Schema(description = "방 이름 (최대 12자)", example = "우테코 회식") String name,
    @Schema(description = "업로드 권한. everyone(누구나) 또는 host(방장만). 생략하면 everyone", example = "everyone") String uploadPolicy,
    @Schema(description = "만료 시간(시간 단위). 24 또는 72만 가능. 생략하면 24", example = "24") Integer expiryHours
) {
}
