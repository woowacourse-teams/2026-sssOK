package com.sssok.presentation.api.room;

import com.sssok.application.room.JoinRoomResult;
import com.sssok.application.room.RoomMembershipService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "방 참여", description = "내 방 목록·입장")
@RestController
@RequiredArgsConstructor
public class RoomMembershipController {

    private final RoomMembershipService roomMembershipService;

    @Operation(summary = "내 방 목록", description = "만든 방과 참여 중인 방을 최근 참여순으로 조회한다. "
        + "삭제된 방은 제외하며 isHost로 방장 여부를 구분한다. 썸네일 생성 중이거나 사진이 없으면 thumbnailUrl은 null이다.")
    @GetMapping("/members/me/rooms")
    public ApiResponse<MyRoomsResponse> getMyRooms(
        @Parameter(hidden = true) @AuthMember Long memberId
    ) {
        return ApiResponse.of(new MyRoomsResponse(roomMembershipService.getMyRooms(memberId)));
    }

    @Operation(
        summary = "방 입장",
        description = "이 계정을 방의 참여자로 등록한다. 같은 사람이 여러 번 호출해도 참여 기록은 늘어나지 "
            + "않는다 — 처음 입장이면 201, 이미 입장한 상태에서 다시 호출하면 200을 반환한다(응답 바디는 동일). "
            + "사용법: 방 구독(SSE)이나 업로드 전에 이 API로 먼저 입장 처리를 해둬야 한다 "
            + "(미입장 상태로 구독을 시도하면 403이 난다). 만료·삭제된 방이면 410이 난다."
    )
    @PostMapping("/rooms/{roomId}/members")
    public ResponseEntity<ApiResponse<RoomMemberResponse>> joinRoom(
        @Parameter(hidden = true) @AuthMember Long memberId,
        @Parameter(description = "방 조회 응답의 roomId") @PathVariable Long roomId
    ) {
        JoinRoomResult result = roomMembershipService.join(roomId, memberId);
        HttpStatus status = result.newlyJoined() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(ApiResponse.of(RoomMemberResponse.from(result)));
    }

}
