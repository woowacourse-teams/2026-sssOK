package com.sssok.presentation.api.room;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateRoomRequest(
    @Schema(description = "방 이름 (최대 12자)", example = "우테코 회식") String name,
    @Schema(description = "업로드 권한. everyone(누구나) 또는 host(방장만). 생략하면 everyone", example = "everyone") String uploadPolicy,
    @Schema(description = "만료 기간(일 단위). 1~14 사이 정수. 생략하면 1", example = "1") Integer expiryDays
) {
}
