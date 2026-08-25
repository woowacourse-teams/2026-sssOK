package com.sssok.presentation.api.room;

import com.sssok.application.room.RoomDetail;
import com.sssok.domain.room.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RoomResponse(
    @Schema(description = "방 식별자. 조회 이후 수정/삭제/입장/구독 API는 모두 이 값을 쓴다") Long roomId,
    @Schema(description = "공유 링크에 쓰이는 코드. 방 조회(GET /rooms/{code})에서만 식별자로 쓰인다") String code,
    @Schema(description = "방 이름") String name,
    @Schema(description = "방 상태: ACTIVE(이용 가능) / EXPIRED(기간 만료) / DELETED(삭제됨) / PURGED(영구 삭제됨)") String status,
    @Schema(description = "방장 계정 식별자. 요청자의 userId와 비교해 방장 여부를 판단하면 된다") Long hostId,
    @Schema(description = "방장의 표시 이름") String hostName,
    @Schema(description = "업로드 권한: everyone(누구나) 또는 host(방장만)") String uploadPolicy,
    @Schema(description = "요청자(토큰 주인)가 이 방에 입장한 적이 있는지") boolean joined,
    @Schema(description = "방 만료 시각. 이 시각이 지나면 입장/업로드가 막힌다") Instant expiresAt,
    @Schema(description = "방 생성 시각") Instant createdAt
) {

    public static RoomResponse from(RoomDetail detail) {
        Room room = detail.room();
        return new RoomResponse(
            room.getId(),
            room.getCode().value(),
            room.getName().value(),
            room.getStatus().name(),
            room.getHostId(),
            detail.hostName(),
            room.getUploadPolicy().apiValue(),
            detail.joined(),
            room.getExpiration().expiresAt(),
            room.getCreatedAt()
        );
    }
}
