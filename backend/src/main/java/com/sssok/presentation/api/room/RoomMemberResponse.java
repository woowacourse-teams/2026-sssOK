package com.sssok.presentation.api.room;

import com.sssok.application.room.JoinRoomResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RoomMemberResponse(
    @Schema(description = "입장한 방의 식별자") Long roomId,
    @Schema(description = "입장한 계정(요청자)의 식별자") Long userId,
    @Schema(description = "입장한 계정의 표시 이름 (인증 시점의 닉네임)") String displayName,
    @Schema(description = "이 방의 방장 계정 식별자") Long hostId,
    @Schema(description = "최초로 입장한 시각. 재입장해도 바뀌지 않는다") Instant joinedAt
) {

    public static RoomMemberResponse from(JoinRoomResult result) {
        return new RoomMemberResponse(
            result.roomId(),
            result.userId(),
            result.displayName(),
            result.hostId(),
            result.joinedAt()
        );
    }
}
