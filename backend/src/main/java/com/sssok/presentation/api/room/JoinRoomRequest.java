package com.sssok.presentation.api.room;

import io.swagger.v3.oas.annotations.media.Schema;

public record JoinRoomRequest(
    @Schema(description = "공유 링크·QR로 받은 방 코드 (8자리)", example = "ABCD2345",
        requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(description = "이 방에서 쓸 닉네임. 생략하면 계정 기본 이름을 쓴다", example = "여행자") String nickname
) {
}
